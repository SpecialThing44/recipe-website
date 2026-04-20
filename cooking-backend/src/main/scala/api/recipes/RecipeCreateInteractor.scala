package api.recipes

import api.tags.TagValidationInteractor
import api.users.AuthenticationInteractor
import api.wiki.WikipediaCheck
import com.google.inject.Inject
import context.ApiContext
import domain.ingredients.Unit
import domain.recipes.{Recipe, RecipeInput}
import persistence.ingredients.Ingredients
import persistence.ingredients.weights.IngredientWeightAsyncService
import persistence.recipes.Recipes
import zio.ZIO

class RecipeCreateInteractor @Inject() (
    persistence: Recipes,
    tagValidationInteractor: TagValidationInteractor,
    wikipediaCheck: WikipediaCheck,
    ingredientPersistence: Ingredients,
    richTextSanitizer: RichTextSanitizer,
    ingredientWeightAsyncService: IngredientWeightAsyncService
) {
  def create(input: RecipeInput): ZIO[ApiContext, Throwable, Recipe] = {
    for {
      _ <- RecipeValidator.validateRecipeInput(input)
      maybeUser <- ZIO.service[ApiContext].map(_.applicationContext.user)
      user <- AuthenticationInteractor.ensureIsLoggedIn(maybeUser)
      _ <- tagValidationInteractor.validateNoUnauthorizedNewTags(
        input.tags,
        user.admin
      )
      _ <- input.wikiLink
        .map(wikipediaCheck.validateWikiLink)
        .getOrElse(ZIO.unit)

      sanitizedInstructions <- richTextSanitizer.validateAndSanitize(
        input.instructions
      )
      extractedImageUrls <- richTextSanitizer.extractImageUrls(
        sanitizedInstructions
      )

      ingredients <- zio.ZIO.foreach(input.ingredients) { ii =>
        for {
          ingredient <- ingredientPersistence.getById(ii.ingredientId)
        } yield domain.ingredients.InstructionIngredient(
          ingredient,
          ii.quantity,
          ii.description
        )
      }
      _ <- {
        val anyNonPredefinedUnit = ingredients.exists(instructionIngredient =>
          !Unit.isPredefined(instructionIngredient.quantity.unit)
        )
        if (anyNonPredefinedUnit)
          zio.ZIO.fail(
            domain.types.InputError(
              "Recipe includes ingredient(s) with non-predefined unit"
            )
          )
        else ZIO.unit
      }
      recipeWithSanitized = input.copy(instructions = sanitizedInstructions)
      recipe = RecipeAdapter.adapt(recipeWithSanitized, ingredients, user)
      recipeWithImages = recipe.copy(instructionImages = extractedImageUrls)
      result <- persistence.create(recipeWithImages)
      _ <- ingredientWeightAsyncService
        .enqueueRecipeCreated(result)
        .catchAll(_ => ZIO.unit)
    } yield result
  }
}
