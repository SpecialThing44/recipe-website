package api.ai

import domain.ai.{AiParsedIngredient, AiRecipeParseResponse}
import domain.ingredients.{Ingredient, Unit as IngredientUnit}

object AiResponseValidator {
  private val fallbackUnit = IngredientUnit.Piece.name

  private def normalizeUnitName(unitName: String): String = {
    IngredientUnit
      .fromName(unitName.trim.toLowerCase)
      .map(_.name)
      .getOrElse(fallbackUnit)
  }

  private def sanitizeAndValidateIngredient(
      ingredient: AiParsedIngredient,
      knownIngredientsById: Map[java.util.UUID, Ingredient]
  ): Either[String, AiParsedIngredient] = {
    if ingredient.rawText.trim.isEmpty then {
      Left("Ingredient rawText must be non-empty")
    } else if ingredient.quantity.amount <= 0 then {
      Left(s"Ingredient '${ingredient.rawText}' has non-positive quantity amount")
    } else {
      val canonicalUnit = normalizeUnitName(ingredient.quantity.unit)
      val sanitizedQuantity = ingredient.quantity.copy(unit = canonicalUnit)

      ingredient.ingredientId match {
        case Some(id) =>
          knownIngredientsById.get(id) match {
            case Some(knownIngredient) =>
              Right(
                ingredient.copy(
                  ingredientName = Some(knownIngredient.name),
                  quantity = sanitizedQuantity
                )
              )
            case None =>
              Left(
                s"Ingredient '${ingredient.rawText}' references unknown ingredientId '$id'"
              )
          }
        case None =>
          if ingredient.ingredientName.nonEmpty then {
            Left(
              s"Ingredient '${ingredient.rawText}' must not set ingredientName when ingredientId is null"
            )
          } else {
            Right(
              ingredient.copy(
                ingredientName = None,
                quantity = sanitizedQuantity
              )
            )
          }
      }
    }
  }

  def validateParsedRecipe(
      parsed: AiRecipeParseResponse,
      knownIngredients: Seq[Ingredient]
  ): Either[String, AiRecipeParseResponse] = {
    if parsed.name.trim.isEmpty then {
      Left("Parsed recipe name must be non-empty")
    } else if parsed.instructions.trim.isEmpty then {
      Left("Parsed recipe instructions must be non-empty")
    } else {
      val knownIngredientsById = knownIngredients.map(i => i.id -> i).toMap
      val validatedIngredientsResult = parsed.ingredients.foldLeft(Right(Seq.empty): Either[String, Seq[AiParsedIngredient]]) {
        case (Left(err), _) => Left(err)
        case (Right(acc), ingredient) =>
          sanitizeAndValidateIngredient(ingredient, knownIngredientsById).map(acc :+ _)
      }

      validatedIngredientsResult.map(validated => parsed.copy(ingredients = validated))
    }
  }
}