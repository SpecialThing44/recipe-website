package api.recipes

import api.users.AuthenticationInteractor
import api.wiki.WikipediaCheck
import com.google.inject.Inject
import context.ApiContext
import domain.recipes.{Recipe, RecipeInput}
import persistence.recipes.Recipes
import persistence.ingredients.Ingredients
import zio.ZIO

class RecipeCreateInteractor @Inject() (
    persistence: Recipes,
    wikipediaCheck: WikipediaCheck,
    ingredientPersistence: Ingredients
) {
  def create(input: RecipeInput): ZIO[ApiContext, Throwable, Recipe] = {
    for {
      maybeUser <- ZIO.service[ApiContext].map(_.applicationContext.user)
      user <- AuthenticationInteractor.ensureIsLoggedIn(maybeUser)
      _ <- input.wikiLink.map(wikipediaCheck.validateWikiLink).getOrElse(ZIO.unit)
      ingredients <- zio.ZIO.foreach(input.ingredients) { ii =>
        for {
          ingredient <- ingredientPersistence.getById(ii.ingredientId)
        } yield domain.ingredients.InstructionIngredient(ingredient, ii.quantity)
      }
      _ <- {
        val anyNonVegetarian = ingredients.exists(!_.ingredient.vegetarian)
        val anyNonVegan = ingredients.exists(!_.ingredient.vegan)
        if (input.vegan && anyNonVegan)
          zio.ZIO.fail(domain.types.InputError("Recipe marked vegan but includes non-vegan ingredient(s)"))
        else if (input.vegetarian && anyNonVegetarian)
          zio.ZIO.fail(domain.types.InputError("Recipe marked vegetarian but includes non-vegetarian ingredient(s)"))
        else ZIO.unit
      }
      recipe = RecipeAdapter.adapt(input, ingredients, user)
      result <- persistence.create(recipe)
    } yield result
  }
}
