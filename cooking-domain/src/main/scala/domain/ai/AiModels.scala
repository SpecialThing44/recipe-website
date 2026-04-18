package domain.ai

import java.util.UUID
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}

case class AiParseParams(text: String)
object AiParseParams {
  implicit val decoder: Decoder[AiParseParams] = deriveDecoder
  implicit val encoder: Encoder[AiParseParams] = deriveEncoder
}

case class AiParsedIngredient(
    rawText: String,
    ingredientId: Option[UUID],
    ingredientName: Option[String],
    quantity: AiParsedQuantity,
    description: Option[String]
)
object AiParsedIngredient {
  implicit val decoder: Decoder[AiParsedIngredient] = deriveDecoder
  implicit val encoder: Encoder[AiParsedIngredient] = deriveEncoder
}

case class AiParsedQuantity(
    amount: Double,
    unit: String
)
object AiParsedQuantity {
  implicit val decoder: Decoder[AiParsedQuantity] = deriveDecoder
  implicit val encoder: Encoder[AiParsedQuantity] = deriveEncoder
}

case class AiRecipeParseResponse(
    name: String,
    instructions: String,
    prepTime: Option[Int],
    cookTime: Option[Int],
    servings: Option[Int],
    tags: Seq[String],
    ingredients: Seq[AiParsedIngredient]
)
object AiRecipeParseResponse {
  implicit val decoder: Decoder[AiRecipeParseResponse] = deriveDecoder
  implicit val encoder: Encoder[AiRecipeParseResponse] = deriveEncoder
}
