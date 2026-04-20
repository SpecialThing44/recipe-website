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
                                         tagValidationService: TagValidationInteractor,
                                         wikipediaCheck: WikipediaCheck,
                                         ingredientPersistence: Ingredients,
                                         richTextSanitizer: RichTextSanitizer,
                                         ingredientWeightAsyncService: IngredientWeightAsyncService
) {
  def update(
      input: RecipeUpdateInput,
      originalRecipe: Recipe
  ): ZIO[ApiContext, Throwable, Recipe] = {
    for {
      _ <- RecipeValidator.validateRecipeUpdateInput(input, originalRecipe)
      context <- ZIO.service[ApiContext]
      _ <- AuthenticationInteractor.ensureAuthenticatedAndMatchingUser(
        context.applicationContext.user,
        originalRecipe.createdBy.id
      )

      _ <- input.tags match {
        case Some(tags) if tags.nonEmpty =>
          tagValidationService.validateNoUnauthorizedNewTags(
            tags,
            context.applicationContext.user.exists(_.admin)
          )
        case _ => ZIO.unit
      }

      // Validate and sanitize instructions if updated
      sanitizedInstructions <- input.instructions match {
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
        if (input.wikiLink.isDefined)
          wikipediaCheck.validateWikiLink(input.wikiLink.get)
        else ZIO.unit
      resolved <- input.ingredients match {
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
        val ingredientsToCheck = resolved.getOrElse(originalRecipe.ingredients)
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
      inputWithSanitized = input.copy(
        instructions = sanitizedInstructions.orElse(input.instructions),
        instructionImages = extractedImageUrls.orElse(input.instructionImages)
      )
      updated = RecipeAdapter.adaptUpdate(
        inputWithSanitized,
        originalRecipe,
        resolved
      )
      result <- persistence.update(updated, originalRecipe)
      _ <- ingredientWeightAsyncService
        .enqueueRecipeUpdated(originalRecipe, result)
        .catchAll(_ => ZIO.unit)
    } yield result
  }
}
