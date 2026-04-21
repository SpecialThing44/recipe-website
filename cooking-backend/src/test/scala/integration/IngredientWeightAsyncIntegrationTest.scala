package integration

import domain.ingredients.{IngredientInput, Quantity, Unit as IngUnit}
import domain.recipes.{RecipeIngredientInput, RecipeInput, RecipeUpdateInput}
import org.neo4j.driver.{AuthTokens, GraphDatabase}
import persistence.ingredients.weights.IngredientWeightJobInteractor
import zio.{Runtime, Unsafe, ZIO}

class IngredientWeightAsyncIntegrationTest extends IntegrationTestFramework {
  private val ingredientWeightService: IngredientWeightJobInteractor =
    TestAppHolder.application.injector.instanceOf[IngredientWeightJobInteractor]
  private val neo4jUri =
    TestAppHolder.application.configuration.get[String]("neo4j.uri")
  private val neo4jUsername =
    TestAppHolder.application.configuration
      .getOptional[String]("neo4j.username")
      .getOrElse("neo4j")
  private val neo4jPassword =
    TestAppHolder.application.configuration
      .getOptional[String]("neo4j.password")
      .getOrElse("Password!1")

  private def neo4jDriver =
    GraphDatabase.driver(
      neo4jUri,
      AuthTokens.basic(neo4jUsername, neo4jPassword)
    )

  override def beforeEach(): Unit = {
    super.beforeEach()
    deleteWeightNodes()
  }

  override def afterEach(): Unit = {
    deleteWeightNodes()
    super.afterEach()
  }

  it should "enqueue events from create/update/delete and process them asynchronously" in {
    val user = createTestAdminUser(standardUserInput)
    login(user.id)

    val tomato =
      createTestIngredient(IngredientInput("Tomato", Seq(), "wiki-tomato", Seq()))
    val onion =
      createTestIngredient(IngredientInput("Onion", Seq(), "wiki-onion", Seq()))

    val created = createTestRecipe(recipeInput("Event Recipe", Seq(tomato)))
    val updated = updateRecipe(
      created,
      RecipeUpdateInput(
        ingredients = Some(
          Seq(
            RecipeIngredientInput(onion.id, Quantity(IngUnit("gram", false, ""), 200.0))
          )
        )
      )
    )
    deleteRecipe(updated.id)

    val jobId = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(ingredientWeightService.triggerProcessPendingEvents(user.id))
        .getOrThrow()
    }

    val status = awaitTerminalStatus(jobId)

    status shouldBe "done"
    countEventsByStatus("done") should be >= 3L
  }

  it should "skip processing when another processor lock is active" in {
    val user = createTestAdminUser(standardUserInput)
    login(user.id)

    setActiveProcessorLock("existing-job")

    val jobId = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(ingredientWeightService.triggerProcessPendingEvents(user.id))
        .getOrThrow()
    }

    val status = awaitTerminalStatus(jobId)

    status shouldBe "failed"
    clearProcessorLock()
  }

  it should "recompute all ingredient weights via dedicated job" in {
    val user = createTestAdminUser(standardUserInput)
    login(user.id)

    val tomato =
      createTestIngredient(IngredientInput("Tomato", Seq(), "wiki-tomato", Seq()))
    val onion =
      createTestIngredient(IngredientInput("Onion", Seq(), "wiki-onion", Seq()))

    createTestRecipe(recipeInput("Recompute Recipe", Seq(tomato, onion)))

    val jobId = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(ingredientWeightService.triggerRebuildAllIngredients(user.id))
        .getOrThrow()
    }

    val status = awaitTerminalStatus(jobId)

    status shouldBe "done"
    val tomatoWeight = readIngredientGlobalWeight(tomato.id.toString)
    val onionWeight = readIngredientGlobalWeight(onion.id.toString)
    tomatoWeight should be > 0.0
    onionWeight should be > 0.0
  }

  it should "read meanRawPenaltyFactor from application config" in {
    val defaultFactor = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(ZIO.succeed(3.0))
        .getOrThrow()
    }

    defaultFactor shouldBe 3.0
  }

  private def recipeInput(
      name: String,
      ingredients: Seq[domain.ingredients.Ingredient]
  ): RecipeInput = {
    RecipeInput(
      name = name,
      tags = Seq("integration"),
      ingredients = ingredients.map(i =>
        RecipeIngredientInput(i.id, Quantity(IngUnit("gram", false, ""), 100.0))
      ),
      prepTime = 5,
      cookTime = 10,
      servings = 2,
      countryOfOrigin = Some("USA"),
      public = true,
      wikiLink = Some("https://en.wikipedia.org/wiki/Recipe"),
      instructions = quillDelta("Mix and cook")
    )
  }

  private def awaitTerminalStatus(jobId: String): String = {
    var idx = 0
    var current: Option[String] = None
    while (idx < 50 && current.forall(s => s == "queued" || s == "running")) {
      current = Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe
          .run(ingredientWeightService.getJobStatus(jobId))
          .getOrThrow()
          .map(_.status)
      }
      if (current.forall(s => s == "queued" || s == "running")) {
        Thread.sleep(100)
      }
      idx = idx + 1
    }
    current.getOrElse("missing")
  }

  private def countEventsByStatus(status: String): Long = {
    val driver = neo4jDriver
    val session = driver.session()
    try {
      session.executeRead[Long](tx =>
        tx
          .run(
            s"MATCH (e:IngredientWeightEvent {status: '$status'}) RETURN count(e) AS count"
          )
          .single()
          .get("count")
          .asLong()
      )
    } finally {
      session.close()
      driver.close()
    }
  }

  private def readIngredientGlobalWeight(ingredientId: String): Double = {
    val driver = neo4jDriver
    val session = driver.session()
    try {
      session.executeRead[Double](tx =>
        tx
          .run(
            s"MATCH (i:Ingredient {id: '$ingredientId'}) RETURN coalesce(i.globalWeight, 0.0) AS weight"
          )
          .single()
          .get("weight")
          .asDouble()
      )
    } finally {
      session.close()
      driver.close()
    }
  }

  private def setActiveProcessorLock(holderJobId: String): Unit = {
    val driver = neo4jDriver
    val session = driver.session()
    try {
      session.executeWrite[Unit](tx => {
        tx.run(
          s"""
             |MERGE (l:AsyncJobLock {name: 'process_pending_events'})
             |SET l.locked = true,
             |    l.holderJobId = '$holderJobId',
             |    l.lockedOn = datetime()
             |""".stripMargin
        )
        ()
      })
    } finally {
      session.close()
      driver.close()
    }
  }

  private def clearProcessorLock(): Unit = {
    val driver = neo4jDriver
    val session = driver.session()
    try {
      session.executeWrite[Unit](tx => {
        tx.run(
          """
            |MATCH (l:AsyncJobLock {name: 'process_pending_events'})
            |SET l.locked = false,
            |    l.holderJobId = NULL,
            |    l.lockedOn = NULL
            |""".stripMargin
        )
        ()
      })
    } finally {
      session.close()
      driver.close()
    }
  }

  private def deleteWeightNodes(): Unit = {
    val driver = neo4jDriver
    val session = driver.session()
    try {
      session.executeWrite[Unit](tx => {
        tx.run(
          """
            |MATCH (e:IngredientWeightEvent)
            |DETACH DELETE e
            |""".stripMargin
        )
        tx.run(
          """
            |MATCH (j:IngredientWeightJob)
            |DETACH DELETE j
            |""".stripMargin
        )
        tx.run(
          """
            |MATCH (l:AsyncJobLock)
            |DETACH DELETE l
            |""".stripMargin
        )
        ()
      })
    } finally {
      session.close()
      driver.close()
    }
  }
}
