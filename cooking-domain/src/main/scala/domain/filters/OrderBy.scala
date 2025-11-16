package domain.filters

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class OrderBy(
    name: Option[Boolean]
)

object OrderBy {
  implicit val encoder: Encoder[OrderBy] = deriveEncoder[OrderBy]
  implicit val decoder: Decoder[OrderBy] = deriveDecoder[OrderBy]
}
