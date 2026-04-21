package api.recipes

import api.users.AuthenticationInteractor
import com.google.inject.Inject
import context.ApiContext
import domain.recipes.Recipe
import persistence.ingredients.weights.IngredientWeightEventInteractor
import persistence.recipes.Recipes
import zio.ZIO

import java.util.UUID

class RecipeDeleteInteractor @Inject() (
                                         persistence: Recipes,
                                         ingredientWeightEventInteractor: IngredientWeightEventInteractor
) {
  def delete(id: UUID): ZIO[ApiContext, Throwable, Recipe] = {
    for {
      user <- ZIO.service[ApiContext].map(_.applicationContext.user)
      recipe <- persistence.getById(id)
      _ <- AuthenticationInteractor.ensureAuthenticatedAndMatchingUser(
        user,
        recipe.createdBy.id
      )
      deleted <- persistence.delete(id)
      _ <- ingredientWeightEventInteractor
        .enqueueRecipeDeleted(recipe)
        .catchAll(_ => ZIO.unit)
    } yield deleted
  }
}
