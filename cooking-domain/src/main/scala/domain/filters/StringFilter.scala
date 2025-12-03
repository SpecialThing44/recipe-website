package domain.filters

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class StringFilter(
    equals: Option[String],
    anyOf: Option[Seq[String]],
    contains: Option[String],
    startsWith: Option[String],
    endsWith: Option[String]
) {
  def sanitize: StringFilter = StringFilter(
    equals = equals.map(_.toLowerCase),
    anyOf = anyOf.map(_.map(_.toLowerCase)),
    contains = contains.map(_.toLowerCase),
    startsWith = startsWith.map(_.toLowerCase),
    endsWith = endsWith.map(_.toLowerCase)
  )
}

object StringFilter {
  def empty(): StringFilter = StringFilter(None, None, None, None, None)
  implicit val encoder: Encoder[StringFilter] = deriveEncoder[StringFilter]
  implicit val decoder: Decoder[StringFilter] = deriveDecoder[StringFilter]
}
