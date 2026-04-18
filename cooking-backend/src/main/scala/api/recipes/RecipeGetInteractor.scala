package api.recipes

import com.google.inject.Inject
import context.ApiContext
import domain.recipes.Recipe
import persistence.recipes.Recipes
import zio.ZIO

import java.util.UUID

class RecipeGetInteractor @Inject() (
    persistence: Recipes
) {
  def getById(id: UUID): ZIO[ApiContext, Throwable, Recipe] =
    persistence.getById(id)
}
