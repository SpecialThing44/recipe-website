package domain.food.ingredients

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class Quantity(
    unit: Unit, // Type!
    amount: Int
)

object Quantity {
  implicit val encoder: Encoder[Quantity] =
    deriveEncoder[Quantity]
  implicit val decoder: Decoder[Quantity] =
    deriveDecoder[Quantity]
}
