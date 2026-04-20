package persistence.ingredients.weights

import com.google.inject.{Inject, Singleton}
import domain.ingredients.Unit as IngredientUnit
import domain.logging.Logging
import domain.recipes.Recipe
import domain.types.ZIORuntime
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder}
import persistence.neo4j.Database
import play.api.Configuration
import zio.{Task, ZIO}

import java.util.UUID
import scala.jdk.CollectionConverters.*

@Singleton
class IngredientWeightAsyncService @Inject() (
  database: Database,
  config: Configuration
)
    extends Logging {

  case class JobStatus(
      jobId: String,
      status: String,
      processedEvents: Int,
      statsJson: Option[String],
      error: Option[String],
      createdAt: String,
      startedAt: Option[String],
      finishedAt: Option[String]
  )

  private case class IngredientVectorItem(
      ingredientId: String,
      rawNormalizedWeight: Double
  )

  private case class IngredientDelta(
      ingredientId: String,
      recipeCountDelta: Int,
      rawDelta: Double
  )

  private case class PendingEvent(
      eventId: String,
      beforeVector: Seq[IngredientVectorItem],
      afterVector: Seq[IngredientVectorItem],
      attempts: Int
  )

  private given Encoder[IngredientVectorItem] = deriveEncoder[IngredientVectorItem]
  private given Decoder[IngredientVectorItem] = deriveDecoder[IngredientVectorItem]
  private given Encoder[IngredientDelta] = deriveEncoder[IngredientDelta]

  private val batchSize =
    sys.env.get("INGREDIENT_WEIGHT_BATCH_SIZE").flatMap(_.toIntOption).getOrElse(100)
  private val maxAttempts =
    sys.env.get("INGREDIENT_WEIGHT_MAX_ATTEMPTS").flatMap(_.toIntOption).getOrElse(5)
  private val baseBackoffSeconds =
    sys.env
      .get("INGREDIENT_WEIGHT_BACKOFF_SECONDS")
      .flatMap(_.toIntOption)
      .getOrElse(15)
  private val defaultMeanRawWeightFactor =
    sys.env
      .get("INGREDIENT_WEIGHT_MEAN_RAW_FACTOR")
      .flatMap(_.toDoubleOption)
      .orElse(config.getOptional[Double]("ingredientWeights.meanRawPenaltyFactor"))
      .getOrElse(3.0)

  def getMeanRawPenaltyFactor(): Task[Double] =
    database.writeTransaction(
      s"""
         |MERGE (settings:IngredientWeightSettings {name: 'default'})
         |ON CREATE SET settings.meanRawPenaltyFactor = $$defaultMeanRawWeightFactor,
         |              settings.updatedAt = datetime()
         |RETURN coalesce(settings.meanRawPenaltyFactor, $$defaultMeanRawWeightFactor) AS meanRawPenaltyFactor
         |""".stripMargin,
      Map("defaultMeanRawWeightFactor" -> Double.box(defaultMeanRawWeightFactor)),
      (result: org.neo4j.driver.Result) => {
        if (result.hasNext) {
          result.next().get("meanRawPenaltyFactor").asDouble()
        } else {
          defaultMeanRawWeightFactor
        }
      }
    )

  def setMeanRawPenaltyFactor(factor: Double): Task[Double] = {
    if (factor < 0.0) {
      ZIO.fail(
        new IllegalArgumentException(
          "meanRawPenaltyFactor must be greater than or equal to 0"
        )
      )
    } else {
      database.writeTransaction(
        s"""
           |MERGE (settings:IngredientWeightSettings {name: 'default'})
           |SET settings.meanRawPenaltyFactor = $$factor,
           |    settings.updatedAt = datetime()
           |RETURN settings.meanRawPenaltyFactor AS meanRawPenaltyFactor
           |""".stripMargin,
        Map("factor" -> Double.box(factor)),
        (result: org.neo4j.driver.Result) => {
          if (result.hasNext) {
            result.next().get("meanRawPenaltyFactor").asDouble()
          } else {
            factor
          }
        }
      )
    }
  }

  def enqueueRecipeCreated(recipe: Recipe): Task[Unit] =
    enqueueEvent(
      "recipe_created",
      recipe.id,
      beforeVector = Seq.empty,
      afterVector = vectorFromRecipe(recipe)
    )

  def enqueueRecipeUpdated(before: Recipe, after: Recipe): Task[Unit] =
    enqueueEvent(
      "recipe_updated",
      after.id,
      beforeVector = vectorFromRecipe(before),
      afterVector = vectorFromRecipe(after)
    )

  def enqueueRecipeDeleted(recipe: Recipe): Task[Unit] =
    enqueueEvent(
      "recipe_deleted",
      recipe.id,
      beforeVector = vectorFromRecipe(recipe),
      afterVector = Seq.empty
    )

  def triggerProcessPendingEvents(requestedBy: UUID): Task[String] = {
    val jobId = UUID.randomUUID().toString
    val createJob = createQueuedJob(jobId, "process_pending_events", requestedBy)

    createJob.map { _ =>
      val _ = ZIORuntime.unsafeFork(runProcessPendingEventsJob(jobId).orDie)
      jobId
    }
  }

  def triggerRebuildAllIngredients(requestedBy: UUID): Task[String] = {
    val jobId = UUID.randomUUID().toString
    val createJob = createQueuedJob(jobId, "rebuild_all", requestedBy)

    createJob.map { _ =>
      val _ = ZIORuntime.unsafeFork(runRebuildAllIngredientsJob(jobId).orDie)
      jobId
    }
  }

  def getJobStatus(jobId: String): Task[Option[JobStatus]] =
    database.readTransaction(
      s"""
         |MATCH (j:IngredientWeightJob {jobId: $$jobId})
         |RETURN j.jobId AS jobId,
         |       j.status AS status,
         |       coalesce(j.processedEvents, 0) AS processedEvents,
         |       j.statsJson AS statsJson,
         |       j.error AS error,
         |       toString(coalesce(j.createdAt, j.createdOn)) AS createdAt,
         |       CASE WHEN coalesce(j.startedAt, j.startedOn) IS NULL THEN NULL ELSE toString(coalesce(j.startedAt, j.startedOn)) END AS startedAt,
         |       CASE WHEN coalesce(j.finishedAt, j.finishedOn) IS NULL THEN NULL ELSE toString(coalesce(j.finishedAt, j.finishedOn)) END AS finishedAt
         |""".stripMargin,
      Map("jobId" -> jobId),
      (result: org.neo4j.driver.Result) =>
        if (!result.hasNext) None
        else {
          val record = result.next()
          Some(
            JobStatus(
              jobId = record.get("jobId").asString(),
              status = record.get("status").asString(),
              processedEvents = record.get("processedEvents").asInt(),
              statsJson =
                if (record.get("statsJson").isNull) None
                else Some(record.get("statsJson").asString()),
              error =
                if (record.get("error").isNull) None
                else Some(record.get("error").asString()),
              createdAt = record.get("createdAt").asString(),
              startedAt =
                if (record.get("startedAt").isNull) None
                else Some(record.get("startedAt").asString()),
              finishedAt =
                if (record.get("finishedAt").isNull) None
                else Some(record.get("finishedAt").asString())
            )
          )
        }
    )

  def getActiveJobIds(): Task[Seq[String]] =
    database.readTransaction(
      """
        |MATCH (j:IngredientWeightJob)
        |WHERE j.status IN ['queued', 'running']
        |RETURN j.jobId AS jobId
        |ORDER BY coalesce(j.createdAt, j.createdOn) DESC
        |""".stripMargin,
      (result: org.neo4j.driver.Result) =>
        result.asScala.map(record => record.get("jobId").asString()).toSeq
    )

  private def vectorFromRecipe(recipe: Recipe): Seq[IngredientVectorItem] = {
    val ingredientWeights = recipe.ingredients.map(instructionIngredient =>
      (
        instructionIngredient.ingredient.id.toString,
        IngredientUnit.toStandardizedAmount(
          instructionIngredient.quantity.unit,
          instructionIngredient.quantity.amount
        ).toDouble
      )
    )
    val totalStandardized = ingredientWeights.map(_._2).sum
    ingredientWeights
      .map { case (ingredientId, standardizedAmount) =>
        val rawNormalizedWeight =
          if (totalStandardized == 0) 0.0
          else standardizedAmount / totalStandardized
        IngredientVectorItem(ingredientId, rawNormalizedWeight)
      }
      .groupBy(_.ingredientId)
      .map { case (ingredientId, entries) =>
        IngredientVectorItem(
          ingredientId,
          entries.map(_.rawNormalizedWeight).sum
        )
      }
      .toSeq
  }

  private def enqueueEvent(
      eventType: String,
      recipeId: UUID,
      beforeVector: Seq[IngredientVectorItem],
      afterVector: Seq[IngredientVectorItem]
  ): Task[Unit] = {
    val eventId = UUID.randomUUID().toString
    val beforeVectorJson = beforeVector.asJson.noSpaces.replace("'", "")
    val afterVectorJson = afterVector.asJson.noSpaces.replace("'", "")
    database.writeTransaction(
      s"""
         |CREATE (e:IngredientWeightEvent {
         |  eventId: $$eventId,
         |  eventType: $$eventType,
         |  recipeId: $$recipeId,
         |  beforeVector: $$beforeVector,
         |  afterVector: $$afterVector,
         |  createdAt: datetime(),
         |  status: 'pending',
         |  attempts: 0
         |})
         |RETURN e.eventId AS eventId
         |""".stripMargin,
      Map(
        "eventId" -> eventId,
        "eventType" -> eventType,
        "recipeId" -> recipeId.toString,
        "beforeVector" -> beforeVectorJson,
        "afterVector" -> afterVectorJson
      ),
      (_: org.neo4j.driver.Result) => ()
    )
  }

  private def createQueuedJob(
      jobId: String,
      jobType: String,
      requestedBy: UUID
  ): Task[Unit] =
    database.writeTransaction(
      s"""
         |CREATE (j:IngredientWeightJob {
         |  jobId: $$jobId,
         |  jobType: $$jobType,
         |  status: 'queued',
         |  requestedBy: $$requestedBy,
         |  createdAt: datetime(),
         |  createdOn: datetime()
         |})
         |RETURN j.jobId AS jobId
         |""".stripMargin,
      Map(
        "jobId" -> jobId,
        "jobType" -> jobType,
        "requestedBy" -> requestedBy.toString
      ),
      (_: org.neo4j.driver.Result) => ()
    )

  private def runProcessPendingEventsJob(jobId: String): Task[Unit] = {
    val markRunning =
      database.writeTransaction(
        s"""
           |MATCH (j:IngredientWeightJob {jobId: $$jobId})
           |SET j.status = 'running',
           |    j.startedAt = datetime(),
           |    j.startedOn = datetime()
           |RETURN j.jobId AS jobId
           |""".stripMargin,
        Map("jobId" -> jobId),
        (_: org.neo4j.driver.Result) => ()
      )

    val run =
      for {
        lockAcquired <- tryAcquireProcessorLock(jobId)
        _ <-
          if (!lockAcquired)
            markJobFailed(jobId, "Another processor is already running")
          else
            for {
              meanRawPenaltyFactor <- getMeanRawPenaltyFactor()
              _ <- markRunning
              processed <- loopWithFactor(0, meanRawPenaltyFactor)
              _ <- markJobDone(jobId, processed, s"{\"processedEvents\":$processed}")
            } yield ()
      } yield ()

    run.catchAll(error =>
      markJobFailed(jobId, error.getMessage).orDie *> ZIO.fail(error)
    ).ensuring(releaseProcessorLock(jobId).ignore)
  }

  private def loopWithFactor(
      processed: Int,
      meanRawPenaltyFactor: Double
  ): Task[Int] =
    for {
      pending <- claimPendingEvents(batchSize)
      batchCount <-
        if (pending.isEmpty) ZIO.succeed(0)
        else
          zio.ZIO
            .foreach(pending) { event =>
              processPendingEvent(event, meanRawPenaltyFactor)
                .as(1)
                .catchAll(error => {
                  logger.error(
                    s"Failed processing ingredient weight event ${event.eventId}: ${error.getMessage}"
                  )
                  markEventRetryOrFailed(
                    event.eventId,
                    event.attempts,
                    error.getMessage
                  ).as(0)
                })
            }
            .map(_.sum)
      totalProcessed = processed + batchCount
      finalProcessed <-
        if (pending.isEmpty) ZIO.succeed(totalProcessed)
        else loopWithFactor(totalProcessed, meanRawPenaltyFactor)
    } yield finalProcessed

  private def runRebuildAllIngredientsJob(jobId: String): Task[Unit] = {
    val markRunning =
      database.writeTransaction(
        s"""
           |MATCH (j:IngredientWeightJob {jobId: $$jobId})
           |SET j.status = 'running',
           |    j.startedAt = datetime(),
           |    j.startedOn = datetime()
           |RETURN j.jobId AS jobId
           |""".stripMargin,
        Map("jobId" -> jobId),
        (_: org.neo4j.driver.Result) => ()
      )

    val run =
      for {
        lockAcquired <- tryAcquireProcessorLock(jobId)
        _ <-
          if (!lockAcquired)
            markJobFailed(jobId, "Another processor is already running")
          else
            for {
              meanRawPenaltyFactor <- getMeanRawPenaltyFactor()
              _ <- markRunning
              ingredientIds <- getAllIngredientIds()
              _ <- recomputeIngredientStats(ingredientIds, meanRawPenaltyFactor)
              processed = ingredientIds.distinct.size
              _ <-
                markJobDone(jobId, processed, s"{\"processedIngredients\":$processed}")
            } yield ()
      } yield ()

    run.catchAll(error =>
      markJobFailed(jobId, error.getMessage).orDie *> ZIO.fail(error)
    ).ensuring(releaseProcessorLock(jobId).ignore)
  }

  private def claimPendingEvents(limit: Int): Task[Seq[PendingEvent]] =
    database.writeTransaction(
      s"""
         |MATCH (e:IngredientWeightEvent)
         |WHERE (
         |  e.status = 'pending' OR
         |  (e.status = 'retry' AND coalesce(e.nextEligibleAt, e.nextRetryOn) IS NOT NULL AND coalesce(e.nextEligibleAt, e.nextRetryOn) <= datetime())
         |)
         |  AND coalesce(e.attempts, 0) < $$maxAttempts
         |WITH e
         |ORDER BY coalesce(e.createdAt, e.createdOn) ASC
         |LIMIT $$limit
         |SET e.status = 'processing',
         |    e.startedAt = datetime(),
         |    e.startedOn = datetime(),
         |    e.attempts = coalesce(e.attempts, 0) + 1
         |RETURN e.eventId AS eventId,
         |       e.beforeVector AS beforeVector,
         |       e.afterVector AS afterVector,
         |       e.attempts AS attempts
         |""".stripMargin,
      Map(
        "maxAttempts" -> Int.box(maxAttempts),
        "limit" -> Int.box(limit)
      ),
      (result: org.neo4j.driver.Result) =>
        result.asScala
          .map(record => {
            val eventId = record.get("eventId").asString()
            val beforeVector =
              if (record.get("beforeVector").isNull) Seq.empty
              else fromJsonVector(record.get("beforeVector").asString())
            val afterVector =
              if (record.get("afterVector").isNull) Seq.empty
              else fromJsonVector(record.get("afterVector").asString())
            val attempts = record.get("attempts").asInt()
            PendingEvent(eventId, beforeVector, afterVector, attempts)
          })
          .toSeq
    )

  private def fromJsonVector(json: String): Seq[IngredientVectorItem] =
    decode[Seq[IngredientVectorItem]](Option(json).getOrElse("[]")).getOrElse(Seq.empty)

  private def processPendingEvent(
      event: PendingEvent,
      meanRawPenaltyFactor: Double
  ): Task[Unit] =
    for {
      _ <- applyIngredientDeltas(
        deltasFrom(event.beforeVector, event.afterVector),
        meanRawPenaltyFactor
      )
      _ <- markEventDone(event.eventId)
    } yield ()

  private def deltasFrom(
      beforeVector: Seq[IngredientVectorItem],
      afterVector: Seq[IngredientVectorItem]
  ): Seq[IngredientDelta] = {
    val beforeById = beforeVector.map(item => item.ingredientId -> item).toMap
    val afterById = afterVector.map(item => item.ingredientId -> item).toMap
    val allIds = (beforeById.keySet ++ afterById.keySet).toSeq
    allIds
      .map(ingredientId => {
        val beforeWeight = beforeById.get(ingredientId).map(_.rawNormalizedWeight).getOrElse(0.0)
        val afterWeight = afterById.get(ingredientId).map(_.rawNormalizedWeight).getOrElse(0.0)
        val inBefore = beforeById.contains(ingredientId)
        val inAfter = afterById.contains(ingredientId)
        val recipeCountDelta =
          if (inBefore && !inAfter) -1
          else if (!inBefore && inAfter) 1
          else 0
        IngredientDelta(
          ingredientId = ingredientId,
          recipeCountDelta = recipeCountDelta,
          rawDelta = afterWeight - beforeWeight
        )
      })
      .filter(delta => delta.recipeCountDelta != 0 || math.abs(delta.rawDelta) > 1e-12)
  }

  private def applyIngredientDeltas(
      deltas: Seq[IngredientDelta],
      meanRawPenaltyFactor: Double
  ): Task[Unit] =
    if (deltas.isEmpty) ZIO.unit
    else
      zio.ZIO.foreach(deltas) { delta =>
        database.writeTransaction(
          s"""
             |MATCH (allRecipes:Recipe)
             |WITH count(allRecipes) AS totalRecipes
             |MATCH (ingredient:Ingredient {id: $$ingredientId})
             |WITH ingredient, totalRecipes,
             |     $$recipeCountDelta AS recipeCountDelta,
             |     $$rawDelta AS rawDelta
             |WITH ingredient, totalRecipes,
             |     CASE
             |       WHEN coalesce(ingredient.recipeCount, 0) + recipeCountDelta < 0 THEN 0
             |       ELSE coalesce(ingredient.recipeCount, 0) + recipeCountDelta
             |     END AS newRecipeCount,
             |     coalesce(ingredient.sumRawNormalizedWeight, 0.0) + rawDelta AS newSumRaw
             |WITH ingredient, totalRecipes, newRecipeCount, newSumRaw,
             |     CASE WHEN newRecipeCount = 0 THEN 0.0 ELSE newSumRaw / toFloat(newRecipeCount) END AS newMeanRaw
             |WITH ingredient, totalRecipes, newRecipeCount, newSumRaw, newMeanRaw,
             |     (log((toFloat(totalRecipes) + 1.0) / (toFloat(newRecipeCount) + 1.0)) + 1.0) AS idfWeight,
             |     (1.0 / (1.0 + ($$meanRawPenaltyFactor * newMeanRaw))) AS quantityPenalty
             |SET ingredient.recipeCount = newRecipeCount,
             |    ingredient.sumRawNormalizedWeight = newSumRaw,
             |    ingredient.meanRawNormalizedWeight = newMeanRaw,
             |    ingredient.globalWeight = CASE
             |      WHEN totalRecipes = 0 OR newRecipeCount = 0 THEN 1.0
             |      ELSE idfWeight * quantityPenalty
             |    END,
             |    ingredient.weightUpdatedAt = datetime(),
             |    ingredient.weightVersion = coalesce(ingredient.weightVersion, 0) + 1
             |RETURN count(ingredient) AS updatedCount
             |""".stripMargin,
          Map(
            "ingredientId" -> delta.ingredientId,
            "recipeCountDelta" -> Int.box(delta.recipeCountDelta),
            "rawDelta" -> Double.box(delta.rawDelta),
            "meanRawPenaltyFactor" -> Double.box(meanRawPenaltyFactor)
          ),
          (_: org.neo4j.driver.Result) => ()
        )
      }.unit

  private def getAllIngredientIds(): Task[Seq[String]] =
    database.readTransaction(
      """
        |MATCH (ingredient:Ingredient)
        |RETURN ingredient.id AS ingredientId
        |""".stripMargin,
      (result: org.neo4j.driver.Result) =>
        result.asScala.map(record => record.get("ingredientId").asString()).toSeq
    )

  private def recomputeIngredientStats(
      ingredientIds: Seq[String],
      meanRawPenaltyFactor: Double
  ): Task[Unit] =
    if (ingredientIds.isEmpty) ZIO.unit
    else {
      database.writeTransaction(
        s"""
           |MATCH (allRecipes:Recipe)
           |WITH count(allRecipes) AS totalRecipes
           |UNWIND $$ingredientIds AS ingredientId
           |MATCH (ingredient:Ingredient {id: ingredientId})
           |OPTIONAL MATCH (recipe:Recipe)-[ri:HAS_INGREDIENT]->(ingredient)
           |WITH ingredient, totalRecipes,
           |     count(DISTINCT recipe) AS recipeCount,
           |     coalesce(sum(coalesce(ri.rawNormalizedWeight, ri.normalizedWeight, 0.0)), 0.0) AS sumRawNormalizedWeight
           |WITH ingredient, totalRecipes, recipeCount, sumRawNormalizedWeight,
           |     CASE WHEN recipeCount = 0 THEN 0.0 ELSE sumRawNormalizedWeight / toFloat(recipeCount) END AS meanRawNormalizedWeight
           |WITH ingredient, totalRecipes, recipeCount, sumRawNormalizedWeight, meanRawNormalizedWeight,
           |     (log((toFloat(totalRecipes) + 1.0) / (toFloat(recipeCount) + 1.0)) + 1.0) AS idfWeight,
           |     (1.0 / (1.0 + ($$meanRawPenaltyFactor * meanRawNormalizedWeight))) AS quantityPenalty
           |SET ingredient.recipeCount = recipeCount,
           |    ingredient.sumRawNormalizedWeight = sumRawNormalizedWeight,
           |    ingredient.meanRawNormalizedWeight = meanRawNormalizedWeight,
           |    ingredient.globalWeight = CASE
           |      WHEN totalRecipes = 0 OR recipeCount = 0 THEN 1.0
           |      ELSE idfWeight * quantityPenalty
           |    END,
           |    ingredient.weightUpdatedAt = datetime(),
           |    ingredient.weightVersion = coalesce(ingredient.weightVersion, 0) + 1
           |RETURN count(ingredient) AS updatedCount
           |""".stripMargin,
        Map(
          "ingredientIds" -> ingredientIds.distinct.asJava,
          "meanRawPenaltyFactor" -> Double.box(meanRawPenaltyFactor)
        ),
        (_: org.neo4j.driver.Result) => ()
      )
    }

  private def markEventDone(eventId: String): Task[Unit] =
    database.writeTransaction(
      s"""
         |MATCH (e:IngredientWeightEvent {eventId: $$eventId})
         |SET e.status = 'done',
         |    e.completedAt = datetime(),
         |    e.completedOn = datetime()
         |RETURN e.eventId AS eventId
         |""".stripMargin,
      Map("eventId" -> eventId),
      (_: org.neo4j.driver.Result) => ()
    )

  private def markEventRetryOrFailed(
      eventId: String,
      attempts: Int,
      error: String
  ): Task[Unit] = {
    val sanitized = Option(error).getOrElse("unknown")
    if (attempts >= maxAttempts) {
      database.writeTransaction(
        s"""
           |MATCH (e:IngredientWeightEvent {eventId: $$eventId})
           |SET e.status = 'failed',
           |    e.lastError = $$lastError,
           |    e.failedAt = datetime(),
           |    e.failedOn = datetime()
           |RETURN e.eventId AS eventId
           |""".stripMargin,
        Map(
          "eventId" -> eventId,
          "lastError" -> sanitized
        ),
        (_: org.neo4j.driver.Result) => ()
      )
    } else {
      val backoffSeconds =
        baseBackoffSeconds * math.pow(2.0, attempts.toDouble - 1.0).toInt
      database.writeTransaction(
        s"""
           |MATCH (e:IngredientWeightEvent {eventId: $$eventId})
           |SET e.status = 'retry',
           |    e.lastError = $$lastError,
           |    e.nextEligibleAt = datetime() + duration({seconds: $$backoffSeconds}),
           |    e.nextRetryOn = datetime() + duration({seconds: $$backoffSeconds})
           |RETURN e.eventId AS eventId
           |""".stripMargin,
        Map(
          "eventId" -> eventId,
          "lastError" -> sanitized,
          "backoffSeconds" -> Int.box(backoffSeconds)
        ),
        (_: org.neo4j.driver.Result) => ()
      )
    }
  }

  private def tryAcquireProcessorLock(jobId: String): Task[Boolean] =
    database.writeTransaction(
      s"""
         |MERGE (l:IngredientWeightProcessorLock {name: 'default'})
         |WITH l
         |WHERE coalesce(l.locked, false) = false
         |SET l.locked = true,
         |    l.holderJobId = $$jobId,
         |    l.lockedOn = datetime()
         |RETURN l.name AS lockName
         |""".stripMargin,
      Map("jobId" -> jobId),
      (result: org.neo4j.driver.Result) => result.hasNext
    )

  private def releaseProcessorLock(jobId: String): Task[Unit] =
    database.writeTransaction(
      s"""
         |MATCH (l:IngredientWeightProcessorLock {name: 'default'})
         |WHERE l.holderJobId = $$jobId
         |SET l.locked = false,
         |    l.holderJobId = NULL,
         |    l.lockedOn = NULL
         |RETURN l.name AS lockName
         |""".stripMargin,
      Map("jobId" -> jobId),
      (_: org.neo4j.driver.Result) => ()
    )

  private def markJobDone(
      jobId: String,
      processedEvents: Int,
      statsJson: String
  ): Task[Unit] =
    database.writeTransaction(
      s"""
         |MATCH (j:IngredientWeightJob {jobId: $$jobId})
         |SET j.status = 'done',
         |    j.processedEvents = $$processedEvents,
         |    j.statsJson = $$statsJson,
         |    j.finishedAt = datetime(),
         |    j.finishedOn = datetime()
         |RETURN j.jobId AS jobId
         |""".stripMargin,
      Map(
        "jobId" -> jobId,
        "processedEvents" -> Int.box(processedEvents),
        "statsJson" -> statsJson
      ),
      (_: org.neo4j.driver.Result) => ()
    )

  private def markJobFailed(jobId: String, error: String): Task[Unit] = {
    val sanitized = Option(error).getOrElse("unknown")
    database.writeTransaction(
      s"""
         |MATCH (j:IngredientWeightJob {jobId: $$jobId})
         |SET j.status = 'failed',
         |    j.error = $$error,
         |    j.finishedAt = datetime(),
         |    j.finishedOn = datetime()
         |RETURN j.jobId AS jobId
         |""".stripMargin,
      Map(
        "jobId" -> jobId,
        "error" -> sanitized
      ),
      (_: org.neo4j.driver.Result) => ()
    )
  }
}
