package api.recipes

import api.users.AuthenticationInteractor
import api.wiki.WikipediaCheck
import com.google.inject.Inject
import context.ApiContext
import domain.recipes.{Recipe, RecipeUpdateInput}
import persistence.ingredients.Ingredients
import zio.ZIO
import persistence.recipes.Recipes

class RecipeUpdateInteractor @Inject() (
    persistence: Recipes,
    wikipediaCheck: WikipediaCheck,
    ingredientPersistence: Ingredients
) {
  def update(
      input: RecipeUpdateInput,
      originalRecipe: Recipe
  ): ZIO[ApiContext, Throwable, Recipe] = {
    for {
      context <- ZIO.service[ApiContext]
      _ <- AuthenticationInteractor.ensureAuthenticatedAndMatchingUser(
        context.applicationContext.user,
        originalRecipe.createdBy.id
      )
      _ <- if (input.wikiLink.isDefined) wikipediaCheck.validateWikiLink(input.wikiLink.get) else ZIO.unit
      resolved <- input.ingredients match {
        case Some(list) =>
          zio.ZIO.foreach(list) { ii =>
            for {
              ingredient <- ingredientPersistence.getById(ii.ingredientId)
            } yield domain.ingredients.InstructionIngredient(ingredient, ii.quantity)
          }.map(Some(_))
        case None => ZIO.succeed(None)
      }
      _ <- {
        val targetVegetarian = input.vegetarian.getOrElse(originalRecipe.vegetarian)
        val targetVegan = input.vegan.getOrElse(originalRecipe.vegan)
        val ingredientsToCheck = resolved.getOrElse(originalRecipe.ingredients)
        val anyNonVegetarian = ingredientsToCheck.exists(!_.ingredient.vegetarian)
        val anyNonVegan = ingredientsToCheck.exists(!_.ingredient.vegan)
        if (targetVegan && anyNonVegan)
          ZIO.fail(domain.types.InputError("Recipe marked vegan but includes non-vegan ingredient(s)"))
        else if (targetVegetarian && anyNonVegetarian)
          ZIO.fail(domain.types.InputError("Recipe marked vegetarian but includes non-vegetarian ingredient(s)"))
        else ZIO.unit
      }
      updated = RecipeAdapter.adaptUpdate(input, originalRecipe, resolved)
      result <- persistence.update(updated, originalRecipe)
    } yield result
  }
}
