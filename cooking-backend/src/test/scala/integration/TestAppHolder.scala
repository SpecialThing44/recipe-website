package integration

import api.RecipeApp
import api.ingredients.IngredientsFacade
import api.recipes.RecipeFacade
import api.users.UserFacade
import api.wiki.WikipediaCheck
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder

/**
 * A singleton holder to ensure a single Guice application and a single database across all integration tests.
 */
object TestAppHolder {
  // Build application lazily and once for the whole JVM/test run
  lazy val application = new GuiceApplicationBuilder()
    .configure("neo4j.isEmbedded" -> true)
    .overrides(bind[WikipediaCheck].to[integration.stubs.FakeWikipediaCheck])
    .build()

  // Lazily fetched components
  lazy val recipeApp: RecipeApp = application.injector.instanceOf[RecipeApp]
  lazy val userFacade: UserFacade = application.injector.instanceOf[UserFacade]
  lazy val ingredientsFacade: IngredientsFacade = application.injector.instanceOf[IngredientsFacade]
  lazy val recipeFacade: RecipeFacade = application.injector.instanceOf[RecipeFacade]

  @volatile private var initialized: Boolean = false

  /** Initialize database once. Safe to call multiple times. */
  def initOnce(): Unit = this.synchronized {
    if (!initialized) {
      recipeApp.initialize()
      initialized = true
      // Ensure proper shutdown at JVM end
      Runtime.getRuntime.addShutdownHook(new Thread(() => {
        try recipeApp.shutdown() finally application.stop()
      }))
    }
  }
}
