package domain.recipes

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import java.util.UUID

case class RecipeIngredientInput(
    ingredientId: UUID,
    quantity: domain.ingredients.Quantity
)

object RecipeIngredientInput {
  implicit val encoder: Encoder[RecipeIngredientInput] = deriveEncoder[RecipeIngredientInput]
  implicit val decoder: Decoder[RecipeIngredientInput] = deriveDecoder[RecipeIngredientInput]
}
