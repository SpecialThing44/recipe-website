package api.recipes

import api.tags.TagValidationInteractor
import api.users.AuthenticationInteractor
import api.wiki.WikipediaCheck
import com.google.inject.Inject
import context.ApiContext
import domain.ingredients.Unit
import domain.recipes.{Recipe, RecipeUpdateInput}
import persistence.ingredients.Ingredients
import persistence.ingredients.weights.IngredientWeightAsyncService
import persistence.recipes.Recipes
import zio.ZIO

class RecipeUpdateInteractor @Inject() (
    persistence: Recipes,
    tagValidationInteractor: TagValidationInteractor,
    wikipediaCheck: WikipediaCheck,
    ingredientPersistence: Ingredients,
    richTextSanitizer: RichTextSanitizer,
    ingredientWeightAsyncService: IngredientWeightAsyncService
) {
  def update(
      recipeInput: RecipeUpdateInput,
      originalRecipe: Recipe
  ): ZIO[ApiContext, Throwable, Recipe] = {
    for {
      _ <- RecipeValidator.validateRecipeUpdateInput(
        recipeInput,
        originalRecipe
      )
      context <- ZIO.service[ApiContext]
      _ <- AuthenticationInteractor.ensureAuthenticatedAndMatchingUser(
        context.applicationContext.user,
        originalRecipe.createdBy.id
      )

      _ <- recipeInput.tags match {
        case Some(tags) if tags.nonEmpty =>
          tagValidationInteractor.validateNoUnauthorizedNewTags(
            tags,
            context.applicationContext.user.exists(_.admin)
          )
        case _ => ZIO.unit
      }

      // Validate and sanitize instructions if updated
      sanitizedInstructions <- recipeInput.instructions match {
        case Some(instructions) =>
          richTextSanitizer.validateAndSanitize(instructions).map(Some(_))
        case None => ZIO.succeed(None)
      }
      extractedImageUrls <- sanitizedInstructions match {
        case Some(instructions) =>
          richTextSanitizer.extractImageUrls(instructions).map(Some(_))
        case None => ZIO.succeed(None)
      }

      _ <-
        if (recipeInput.wikiLink.isDefined)
          wikipediaCheck.validateWikiLink(recipeInput.wikiLink.get)
        else ZIO.unit

      resolvedIngredientInstructions <- recipeInput.ingredients match {
        case Some(list) =>
          zio.ZIO
            .foreach(list) { instructionIngredient =>
              for {
                ingredient <- ingredientPersistence.getById(
                  instructionIngredient.ingredientId
                )
              } yield domain.ingredients.InstructionIngredient(
                ingredient,
                instructionIngredient.quantity,
                instructionIngredient.description
              )
            }
            .map(Some(_))
        case None => ZIO.succeed(None)
      }
      _ <- {
        val ingredientsToCheck =
          resolvedIngredientInstructions.getOrElse(originalRecipe.ingredients)
        val anyNonPredefinedUnit =
          ingredientsToCheck.exists(instructionIngredient =>
            !Unit.isPredefined(instructionIngredient.quantity.unit)
          )
        if (anyNonPredefinedUnit)
          ZIO.fail(
            domain.types.InputError(
              "Recipe includes ingredient(s) with non-predefined unit"
            )
          )
        else ZIO.unit
      }
      sanitizedRecipe = recipeInput.copy(
        instructions = sanitizedInstructions.orElse(recipeInput.instructions),
        instructionImages =
          extractedImageUrls.orElse(recipeInput.instructionImages)
      )
      updated = RecipeAdapter.adaptUpdate(
        sanitizedRecipe,
        originalRecipe,
        resolvedIngredientInstructions
      )
      recipe <- persistence.update(updated, originalRecipe)
      _ <- ingredientWeightAsyncService
        .enqueueRecipeUpdated(originalRecipe, recipe)
        .catchAll(_ => ZIO.unit)
    } yield recipe
  }
}
