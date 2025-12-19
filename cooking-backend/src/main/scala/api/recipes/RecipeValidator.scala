package api.recipes

import domain.recipes.{RecipeInput, RecipeUpdateInput}
import domain.types.InputError
import zio.ZIO

object RecipeValidator {
  
  def validateRecipeInput(input: RecipeInput): ZIO[Any, InputError, Unit] = {
    for {
      _ <- validateIngredientQuantities(input.ingredients.map(_.quantity.amount))
      _ <- validatePrepTime(input.prepTime)
      _ <- validateCookTime(input.cookTime)
      _ <- validateServings(input.servings)
    } yield ()
  }
  
  def validateRecipeUpdateInput(
      input: RecipeUpdateInput,
      original: domain.recipes.Recipe
  ): ZIO[Any, InputError, Unit] = {
    for {
      _ <- input.ingredients match {
        case Some(ingredients) =>
          validateIngredientQuantities(ingredients.map(_.quantity.amount))
        case None => ZIO.unit
      }
      _ <- input.prepTime match {
        case Some(prepTime) => validatePrepTime(prepTime)
        case None => ZIO.unit
      }
      _ <- input.cookTime match {
        case Some(cookTime) => validateCookTime(cookTime)
        case None => ZIO.unit
      }
      _ <- input.servings match {
        case Some(servings) => validateServings(servings)
        case None => ZIO.unit
      }
    } yield ()
  }
  
  private def validateIngredientQuantities(
      quantities: Seq[Double]
  ): ZIO[Any, InputError, Unit] = {
    val invalidQuantities = quantities.filter(_ <= 0)
    if (invalidQuantities.nonEmpty) {
      ZIO.fail(
        InputError(
          s"All ingredient quantities must be greater than 0. Found ${invalidQuantities.length} invalid quantities."
        )
      )
    } else {
      ZIO.unit
    }
  }
  
  private def validatePrepTime(prepTime: Int): ZIO[Any, InputError, Unit] = {
    if (prepTime < 0) {
      ZIO.fail(
        InputError(
          s"Prep time must be non-negative. Found: $prepTime"
        )
      )
    } else {
      ZIO.unit
    }
  }
  
  private def validateCookTime(cookTime: Int): ZIO[Any, InputError, Unit] = {
    if (cookTime < 0) {
      ZIO.fail(
        InputError(
          s"Cook time must be non-negative. Found: $cookTime"
        )
      )
    } else {
      ZIO.unit
    }
  }
  
  private def validateServings(servings: Int): ZIO[Any, InputError, Unit] = {
    if (servings < 1) {
      ZIO.fail(
        InputError(
          s"Servings must be at least 1. Found: $servings"
        )
      )
    } else {
      ZIO.unit
    }
  }
}
