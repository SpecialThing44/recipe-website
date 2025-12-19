package api.recipes

import api.users.AuthenticationInteractor
import api.wiki.WikipediaCheck
import com.google.inject.Inject
import context.ApiContext
import domain.filters.{Filters, StringFilter}
import domain.ingredients.Unit
import domain.recipes.{Recipe, RecipeInput}
import persistence.ingredients.Ingredients
import persistence.recipes.Recipes
import persistence.tags.Tags
import zio.ZIO

class RecipeCreateInteractor @Inject() (
    persistence: Recipes,
    tagsPersistence: Tags,
    wikipediaCheck: WikipediaCheck,
    ingredientPersistence: Ingredients,
    richTextSanitizer: RichTextSanitizer
) {
  def create(input: RecipeInput): ZIO[ApiContext, Throwable, Recipe] = {
    for {
      _ <- RecipeValidator.validateRecipeInput(input)
      maybeUser <- ZIO.service[ApiContext].map(_.applicationContext.user)
      user <- AuthenticationInteractor.ensureIsLoggedIn(maybeUser)
      _ <- if (input.tags.nonEmpty) {
        for {
          existingTags <- tagsPersistence.list(
            Filters
              .empty()
              .copy(name =
                Some(StringFilter.empty().copy(anyOf = Some(input.tags)))
              )
          )
          newTags = input.tags.filterNot(existingTags.contains)
          _ <-
            if (newTags.nonEmpty && !user.admin) {
              ZIO.fail(
                domain.types.InputError(
                  s"Only admins can create new tags: ${newTags.mkString(", ")}"
                )
              )
            } else {
              ZIO.unit
            }
        } yield ()
      } else {
        ZIO.unit
      }
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
    } yield result
  }
}
