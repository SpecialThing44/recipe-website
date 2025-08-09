package domain.filters

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class NumberFilter(
    greaterOrEqual: Option[Int],
    lessOrEqual: Option[Int]
)

object NumberFilter {
  implicit val encoder: Encoder[NumberFilter] = deriveEncoder[NumberFilter]
  implicit val decoder: Decoder[NumberFilter] = deriveDecoder[NumberFilter]
}
