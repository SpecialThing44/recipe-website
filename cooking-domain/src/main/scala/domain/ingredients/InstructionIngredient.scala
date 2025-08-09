package domain.ingredients

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class InstructionIngredient(
    ingredient: Ingredient,
    quantity: Quantity
)

object InstructionIngredient {
  implicit val encoder: Encoder[InstructionIngredient] =
    deriveEncoder[InstructionIngredient]
  implicit val decoder: Decoder[InstructionIngredient] =
    deriveDecoder[InstructionIngredient]
}
