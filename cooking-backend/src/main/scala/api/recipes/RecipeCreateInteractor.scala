package api.recipes

import api.users.AuthenticationInteractor
import api.wiki.WikipediaCheck
import com.google.inject.Inject
import context.ApiContext
import domain.recipes.{Recipe, RecipeInput}
import persistence.recipes.Recipes
import persistence.ingredients.Ingredients
import zio.ZIO
import domain.ingredients.Unit

class RecipeCreateInteractor @Inject() (
    persistence: Recipes,
    wikipediaCheck: WikipediaCheck,
    ingredientPersistence: Ingredients,
    richTextSanitizer: RichTextSanitizer
) {
  def create(input: RecipeInput): ZIO[ApiContext, Throwable, Recipe] = {
    for {
      maybeUser <- ZIO.service[ApiContext].map(_.applicationContext.user)
      user <- AuthenticationInteractor.ensureIsLoggedIn(maybeUser)
      _ <- input.wikiLink.map(wikipediaCheck.validateWikiLink).getOrElse(ZIO.unit)
      
      sanitizedInstructions <- richTextSanitizer.validateAndSanitize(input.instructions)
      extractedImageUrls <- richTextSanitizer.extractImageUrls(sanitizedInstructions)
      
      ingredients <- zio.ZIO.foreach(input.ingredients) { ii =>
        for {
          ingredient <- ingredientPersistence.getById(ii.ingredientId)
        } yield domain.ingredients.InstructionIngredient(ingredient, ii.quantity, ii.description)
      }
      _ <- {
        val anyNonVegetarian = ingredients.exists(ingredient => !ingredient.ingredient.vegetarian && !ingredient.ingredient.vegan)
        val anyNonVegan = ingredients.exists(!_.ingredient.vegan)
        if (input.vegan && anyNonVegan)
          zio.ZIO.fail(domain.types.InputError("Recipe marked vegan but includes non-vegan ingredient(s)"))
        else if (input.vegetarian && anyNonVegetarian)
          zio.ZIO.fail(domain.types.InputError("Recipe marked vegetarian but includes non-vegetarian ingredient(s)"))
        else ZIO.unit
      }
      _ <- {
        val anyNonPredefinedUnit = ingredients.exists(instructionIngredient => !Unit.isPredefined(instructionIngredient.quantity.unit))
        if (anyNonPredefinedUnit)
          zio.ZIO.fail(domain.types.InputError("Recipe includes ingredient(s) with non-predefined unit"))
        else ZIO.unit
      }
      recipeWithSanitized = input.copy(instructions = sanitizedInstructions)
      recipe = RecipeAdapter.adapt(recipeWithSanitized, ingredients, user)
      recipeWithImages = recipe.copy(instructionImages = extractedImageUrls)
      result <- persistence.create(recipeWithImages)
    } yield result
  }
}
