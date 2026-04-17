package initialization

import context.{ApiContext, ApplicationContext}
import domain.ingredients.{Ingredient, InstructionIngredient, Quantity, Unit as IngredientUnit}
import domain.recipes.Recipe
import domain.types.ZIORuntime
import domain.users.User
import initialization.embedded.EmbeddedDatabase
import org.neo4j.driver.{AuthTokens, GraphDatabase}
import persistence.ingredients.Ingredients
import persistence.recipes.Recipes
import persistence.users.Users
import play.api.inject.guice.GuiceApplicationBuilder
import zio.{ZEnvironment, ZIO}

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

object DemoDataSeedRunner {

  private val rootIdentity = "a42d19a694518243b1469ec36e08c1f86758a203f6c14688fdf9531fd9f2cb5a"
  private val rootEmail = "root@example.com"

  def main(args: Array[String]): Unit = {
    val application = new GuiceApplicationBuilder().build()
    val injector = application.injector

    val users = injector.instanceOf[Users]
    val ingredients = injector.instanceOf[Ingredients]
    val recipes = injector.instanceOf[Recipes]
    val cookingApi = injector.instanceOf[context.CookingApi]
    val embeddedDatabase = injector.instanceOf[EmbeddedDatabase]
    val config = injector.instanceOf[play.api.Configuration]

    val now = Instant.now()

    val rootUser = User(
      id = UUID.nameUUIDFromBytes("demo-root-user".getBytes(StandardCharsets.UTF_8)),
      name = "Root",
      email = rootEmail,
      identity = rootIdentity,
      countryOfOrigin = Some("US"),
      admin = true,
      createdOn = now,
      updatedOn = now
    )

    val demoUser = User(
      id = UUID.nameUUIDFromBytes("demo-secondary-user".getBytes(StandardCharsets.UTF_8)),
      name = "Demo Cook",
      email = "cook@example.com",
      identity = "demo-secondary-identity",
      countryOfOrigin = Some("IT"),
      admin = false,
      createdOn = now,
      updatedOn = now
    )

    val tomato = Ingredient(
      id = UUID.nameUUIDFromBytes("demo-ingredient-tomato".getBytes(StandardCharsets.UTF_8)),
      name = "Tomato",
      aliases = Seq("Roma Tomato"),
      wikiLink = "https://en.wikipedia.org/wiki/Tomato",
      tags = Seq("vegetable", "fresh"),
      createdBy = rootUser
    )

    val garlic = Ingredient(
      id = UUID.nameUUIDFromBytes("demo-ingredient-garlic".getBytes(StandardCharsets.UTF_8)),
      name = "Garlic",
      aliases = Seq("Garlic Clove"),
      wikiLink = "https://en.wikipedia.org/wiki/Garlic",
      tags = Seq("aromatic"),
      createdBy = rootUser
    )

    val pasta = Ingredient(
      id = UUID.nameUUIDFromBytes("demo-ingredient-pasta".getBytes(StandardCharsets.UTF_8)),
      name = "Pasta",
      aliases = Seq("Spaghetti"),
      wikiLink = "https://en.wikipedia.org/wiki/Pasta",
      tags = Seq("staple"),
      createdBy = rootUser
    )

    val oliveOil = Ingredient(
      id = UUID.nameUUIDFromBytes("demo-ingredient-olive-oil".getBytes(StandardCharsets.UTF_8)),
      name = "Olive Oil",
      aliases = Seq("Extra Virgin Olive Oil"),
      wikiLink = "https://en.wikipedia.org/wiki/Olive_oil",
      tags = Seq("fat", "mediterranean"),
      createdBy = rootUser
    )

    val pastaRecipe = Recipe(
      id = UUID.nameUUIDFromBytes("demo-recipe-spaghetti".getBytes(StandardCharsets.UTF_8)),
      name = "Spaghetti al Pomodoro",
      createdBy = rootUser,
      tags = Seq("italian", "dinner"),
      ingredients = Seq(
        InstructionIngredient(
          pasta,
          Quantity(IngredientUnit.Gram, 250.0),
          Some("Cook until al dente")
        ),
        InstructionIngredient(
          tomato,
          Quantity(IngredientUnit.Gram, 400.0),
          Some("Crushed")
        ),
        InstructionIngredient(
          garlic,
          Quantity(IngredientUnit.Piece, 2.0),
          Some("Minced")
        ),
        InstructionIngredient(
          oliveOil,
          Quantity(IngredientUnit.Tablespoon, 2.0),
          None
        )
      ),
      prepTime = 10,
      cookTime = 25,
      servings = 2,
      countryOfOrigin = Some("IT"),
      public = true,
      wikiLink = None,
      instructions = "{\"ops\":[{\"insert\":\"Boil pasta. Make sauce with olive oil, garlic, and tomato. Combine and serve.\\n\"}]}",
      createdOn = now,
      updatedOn = now
    )

    val bruschettaRecipe = Recipe(
      id = UUID.nameUUIDFromBytes("demo-recipe-bruschetta".getBytes(StandardCharsets.UTF_8)),
      name = "Tomato Bruschetta",
      createdBy = demoUser,
      tags = Seq("appetizer", "italian"),
      ingredients = Seq(
        InstructionIngredient(
          tomato,
          Quantity(IngredientUnit.Gram, 150.0),
          Some("Diced")
        ),
        InstructionIngredient(
          garlic,
          Quantity(IngredientUnit.Piece, 1.0),
          Some("Rubbed on toasted bread")
        ),
        InstructionIngredient(
          oliveOil,
          Quantity(IngredientUnit.Tablespoon, 1.0),
          Some("Drizzle before serving")
        )
      ),
      prepTime = 12,
      cookTime = 5,
      servings = 2,
      countryOfOrigin = Some("IT"),
      public = true,
      wikiLink = None,
      instructions = "{\"ops\":[{\"insert\":\"Toast bread, top with tomato, garlic, and olive oil.\\n\"}]}",
      createdOn = now,
      updatedOn = now
    )

    def runInContext[A](effect: ZIO[ApiContext, Throwable, A], user: User): A = {
      val apiContext = ApiContext(
        api = cookingApi,
        applicationContext = ApplicationContext(Some(user))
      )
      ZIORuntime.unsafeRun(effect.provideEnvironment(ZEnvironment(apiContext)))
    }

    def wipeDatabase(): Unit = {
      val uri = config.get[String]("neo4j.uri")
      val username = config.get[String]("neo4j.username")
      val password = config.get[String]("neo4j.password")
      val driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password))
      try {
        val session = driver.session()
        try {
          session.executeWrite(tx => {
            tx.run("MATCH (n) DETACH DELETE n")
            ()
          })
        } finally {
          session.close()
        }
      } finally {
        driver.close()
      }
    }

    try {
      println("Initializing database container if configured...")
      embeddedDatabase.initializeIfRequired()

      println("Wiping database...")
      wipeDatabase()

      println("Seeding users...")
      val _ = runInContext(users.create(rootUser), rootUser)
      val _ = runInContext(users.create(demoUser), rootUser)

      println("Seeding ingredients...")
      val _ = runInContext(ingredients.create(tomato), rootUser)
      val _ = runInContext(ingredients.create(garlic), rootUser)
      val _ = runInContext(ingredients.create(pasta), rootUser)
      val _ = runInContext(ingredients.create(oliveOil), rootUser)

      println("Seeding recipes...")
      val _ = runInContext(recipes.create(pastaRecipe), rootUser)
      val _ = runInContext(recipes.create(bruschettaRecipe), demoUser)

      // Create one saved relationship so demo data includes social state as well.
      val _ = runInContext(recipes.save(pastaRecipe.id, demoUser.id), demoUser)

      println("Database reset complete.")
      println(s"Root user identity: $rootIdentity")
      println(s"Root user email: $rootEmail")
    } finally {
      embeddedDatabase.shutdown()
    }
  }
}
