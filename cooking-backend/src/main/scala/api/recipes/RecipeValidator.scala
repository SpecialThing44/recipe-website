package api.recipes

import domain.recipes.{RecipeInput, RecipeUpdateInput}
import domain.types.InputError
import zio.ZIO

object RecipeValidator {

  def validateRecipeInput(input: RecipeInput): ZIO[Any, InputError, Unit] = {
    for {
      _ <- validateIngredientQuantities(
        input.ingredients.map(_.quantity.amount) ++ input.recipeIngredients.map(_.quantity.amount)
      )
      _ <- validateHasIngredients(input.ingredients, input.recipeIngredients)
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
      _ <- input.recipeIngredients match {
        case Some(recipes) => validateIngredientQuantities(recipes.map(_.quantity.amount))
        case None          => ZIO.unit
      }
      _ <- validateHasIngredients(
        input.ingredients.getOrElse(original.ingredients.map(i => domain.recipes.RecipeIngredientInput(i.ingredient.id, i.quantity, i.description))),
        input.recipeIngredients.getOrElse(original.recipeIngredients.map(i => domain.recipes.RecipeComponentInput(i.recipe.id, i.quantity, i.description)))
      )
      _ <- input.prepTime match {
        case Some(prepTime) => validatePrepTime(prepTime)
        case None           => ZIO.unit
      }
      _ <- input.cookTime match {
        case Some(cookTime) => validateCookTime(cookTime)
        case None           => ZIO.unit
      }
      _ <- input.servings match {
        case Some(servings) => validateServings(servings)
        case None           => ZIO.unit
      }
    } yield ()
  }

  private def validateHasIngredients(
      ingredients: Seq[domain.recipes.RecipeIngredientInput],
      recipes: Seq[domain.recipes.RecipeComponentInput]
  ): ZIO[Any, InputError, Unit] =
    if (ingredients.nonEmpty || recipes.nonEmpty) ZIO.unit
    else ZIO.fail(InputError("A recipe must include at least one ingredient or recipe"))

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
