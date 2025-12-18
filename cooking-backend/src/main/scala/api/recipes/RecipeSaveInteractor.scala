package api.recipes

import api.users.AuthenticationInteractor
import com.google.inject.Inject
import context.ApiContext
import domain.recipes.Recipe
import domain.types.InputError
import persistence.recipes.Recipes
import zio.ZIO

import java.util.UUID

class RecipeSaveInteractor @Inject() (
    persistence: Recipes
) {
  def save(recipeId: UUID): ZIO[ApiContext, Throwable, Recipe] = {
    for {
      context <- ZIO.service[ApiContext]
      user <- AuthenticationInteractor.ensureIsLoggedIn(
        context.applicationContext.user
      )
      recipe <- persistence.getById(recipeId)
      _ <-
        if (recipe.createdBy.id == user.id)
          ZIO.fail(InputError("Users cannot save their own recipes"))
        else ZIO.unit
      _ <-
        if (!recipe.public)
          ZIO.fail(InputError("Users cannot save private recipes"))
        else ZIO.unit
      saved <- persistence.save(recipeId, user.id)
    } yield saved
  }
}
