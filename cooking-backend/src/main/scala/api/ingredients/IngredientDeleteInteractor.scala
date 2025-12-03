package api.ingredients

import api.users.AuthenticationInteractor
import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.ingredients.Ingredient
import domain.types.InputError
import persistence.ingredients.Ingredients
import persistence.recipes.Recipes
import zio.ZIO

import java.util.UUID

class IngredientDeleteInteractor @Inject() (
    persistence: Ingredients,
    recipePersistence: Recipes,
) {
  def delete(id: UUID): ZIO[ApiContext, Throwable, Ingredient] = {
    for {
      maybeUser <- ZIO.service[ApiContext].map(_.applicationContext.user)
      user <- AuthenticationInteractor.ensureIsLoggedIn(maybeUser)
      _ <- AuthenticationInteractor.ensureIsAdmin(user)
      ingredient <- persistence.getById(id)
      _ <- validateNoRecipeLinks(ingredient)
      deletedIngredient <- persistence.delete(id)
    } yield deletedIngredient
  }

  private def validateNoRecipeLinks(
      ingredient: Ingredient
  ): ZIO[ApiContext, Throwable, Unit] = {
    for {
      recipes <- ZIO.succeed(Seq.empty)
      _ <- recipePersistence.list(
        Filters.empty().copy(ingredients = Some(Seq(ingredient.name)))
      )
      _ <-
        if (recipes.isEmpty) ZIO.unit
        else
          ZIO.fail(
            InputError(
              "Cannot delete ingredient that is used in recipes"
            )
          )
    } yield ()
  }
}
