package domain.filters

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class Filters()

object Filters {
  implicit val encoder: Encoder[Filters] = deriveEncoder[Filters]
  implicit val decoder: Decoder[Filters] = deriveDecoder[Filters]
}
