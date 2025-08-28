package api.recipes

import com.google.inject.Inject
import context.ApiContext
import domain.recipes.Recipe
import domain.types.NotFoundError
import persistence.recipes.Recipes
import zio.ZIO

import java.util.UUID

class RecipeGetInteractor @Inject() (
    persistence: Recipes
) {
  def getById(id: UUID): ZIO[ApiContext, Throwable, Recipe] = {
    for {
      context <- ZIO.service[ApiContext]
      recipe <- persistence.getById(id)
      _ <- {
        val isOwner = context.applicationContext.user.exists(u => u.id == recipe.createdBy.id)
        if (recipe.public || isOwner) ZIO.unit
        else ZIO.fail(NotFoundError(s"Recipe with id $id not found"))
      }
    } yield recipe
  }
}
