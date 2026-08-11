package domain.recipes

import domain.ingredients.Quantity
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.util.UUID

case class RecipeReference(id: UUID, name: String)

case class InstructionRecipe(
    recipe: RecipeReference,
    quantity: Quantity,
    description: Option[String] = None
)

object RecipeReference {
  implicit val encoder: Encoder[RecipeReference] =
    deriveEncoder[RecipeReference]
  implicit val decoder: Decoder[RecipeReference] =
    deriveDecoder[RecipeReference]
}

object InstructionRecipe {
  implicit val encoder: Encoder[InstructionRecipe] =
    deriveEncoder[InstructionRecipe]
  implicit val decoder: Decoder[InstructionRecipe] =
    deriveDecoder[InstructionRecipe]
}
