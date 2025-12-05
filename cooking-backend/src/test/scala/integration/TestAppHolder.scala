package integration

import api.RecipeApp
import api.ingredients.IngredientsFacade
import api.recipes.RecipeFacade
import api.users.UserFacade
import api.wiki.WikipediaCheck
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder

object TestAppHolder {
  lazy val application = new GuiceApplicationBuilder()
    .configure("neo4j.isEmbedded" -> true)
    .configure("openai.skipModeration" -> true)
    .configure(
      "auth.issuer" -> "https://authentik.example.com/application/o/cooking/"
    )
    .configure(
      "auth.jwksUrl" -> "https://authentik.example.com/application/o/cooking/jwks/"
    )
    .overrides(bind[WikipediaCheck].to[integration.stubs.FakeWikipediaCheck])
    .build()

  lazy val recipeApp: RecipeApp = application.injector.instanceOf[RecipeApp]
  lazy val userFacade: UserFacade = application.injector.instanceOf[UserFacade]
  lazy val ingredientsFacade: IngredientsFacade =
    application.injector.instanceOf[IngredientsFacade]
  lazy val recipeFacade: RecipeFacade =
    application.injector.instanceOf[RecipeFacade]

  @volatile private var initialized: Boolean = false

  def initOnce(): Unit = this.synchronized {
    if (!initialized) {
      recipeApp.initialize()
      initialized = true
      Runtime.getRuntime.addShutdownHook(new Thread(() => {
        try recipeApp.shutdown()
        finally application.stop()
      }))
    }
  }
}
