package initialization

import api.RecipeApp
import com.google.inject.{Inject, Singleton}
import play.api.inject.ApplicationLifecycle

@Singleton
class Startup @Inject() (
    val lifecycle: ApplicationLifecycle,
    val recipeApp: RecipeApp
) {
  recipeApp.initialize()
}
