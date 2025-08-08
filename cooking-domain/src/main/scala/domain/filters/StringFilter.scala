package domain.filters

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

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
