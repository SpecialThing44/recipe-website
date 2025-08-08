package domain.filters

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class StringFilter(
    equals: Option[String],
    anyOf: Option[Seq[String]],
    contains: Option[String],
    startsWith: Option[String],
    endsWith: Option[String]
)

object StringFilter {
  implicit val encoder: Encoder[StringFilter] = deriveEncoder[StringFilter]
  implicit val decoder: Decoder[StringFilter] = deriveDecoder[StringFilter]
}
