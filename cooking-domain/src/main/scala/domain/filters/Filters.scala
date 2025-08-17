package domain.filters

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.util.UUID

case class Filters(
    id: Option[UUID],
    ids: Option[List[UUID]],
    belongsToUser: Option[UUID],
    savedByUser: Option[UUID],
    name: Option[StringFilter],
    aliasesOrName: Option[Seq[String]],
    email: Option[StringFilter],
    prepTime: Option[NumberFilter],
    cookTime: Option[NumberFilter],
    vegetarian: Option[Boolean],
    vegan: Option[Boolean],
    public: Option[Boolean],
    tags: Option[Seq[String]],
    ingredients: Option[Seq[String]],
    notIngredients: Option[Seq[String]],
)

object Filters {
  implicit val encoder: Encoder[Filters] = deriveEncoder[Filters]
  implicit val decoder: Decoder[Filters] = deriveDecoder[Filters]

  def empty(): Filters = Filters(
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None
  )
}
