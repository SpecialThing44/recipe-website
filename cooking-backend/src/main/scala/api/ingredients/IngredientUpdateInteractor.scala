package api.ingredients

import api.users.AuthenticationInteractor
import api.wiki.WikipediaCheck
import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.ingredients.{Ingredient, IngredientUpdateInput}
import domain.types.InputError
import persistence.ingredients.Ingredients
import persistence.recipes.Recipes
import zio.ZIO

class IngredientUpdateInteractor @Inject() (
    ingredientPersistence: Ingredients,
    recipePersistence: Recipes,
    wikipediaCheck: WikipediaCheck
) {
  def update(
      input: IngredientUpdateInput,
      originalIngredient: Ingredient,
  ): ZIO[ApiContext, Throwable, Ingredient] = {
    for {
      context <- ZIO.service[ApiContext]
      _ <- AuthenticationInteractor.ensureAuthenticatedAndMatchingUser(
        context.applicationContext.user,
        originalIngredient.createdBy.id
      )
      _ <-
        if (
          Seq(input.name, input.wikiLink, input.vegan, input.vegetarian).exists(
            _.isDefined
          )
        ) validateNoRecipeLinks(originalIngredient)
        else ZIO.succeed(())
      _ <-
        if (input.wikiLink.isDefined)
          wikipediaCheck.validateWikiLink(input.wikiLink.get)
        else ZIO.unit
      updatedIngredient = IngredientAdapter.adaptUpdate(
        input,
        originalIngredient
      )
      result <- ingredientPersistence.update(
        updatedIngredient,
        originalIngredient
      )
    } yield result
  }

  private def validateNoRecipeLinks(
      ingredient: Ingredient
  ): ZIO[ApiContext, Throwable, Unit] = {
    for {
      recipes <-
        recipePersistence.list(
          Filters.empty().copy(ingredients = Some(Seq(ingredient.name)))
        )
      _ <-
        if (recipes.isEmpty) ZIO.unit
        else
          ZIO.fail(
            InputError(
              "Cannot update ingredient that is used in recipes (Expect for tags and aliases)"
            )
          )
    } yield ()
  }
}
