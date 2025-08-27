package domain.ingredients

import domain.shared.{Identified, Wikified}
import domain.users.User
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.util.UUID

case class Ingredient(
    name: String,
    aliases: Seq[String],
    wikiLink: String,
    vegetarian: Boolean,
    vegan: Boolean,
    tags: Seq[String],
    createdBy: User,
    id: UUID
) extends Wikified
    with Identified

case class IngredientInput(
    name: String,
    aliases: Seq[String],
    wikiLink: String,
    vegetarian: Boolean,
    vegan: Boolean,
    tags: Seq[String],
)

case class IngredientUpdateInput(
    name: Option[String],
    aliases: Option[Seq[String]],
    wikiLink: Option[String],
    vegetarian: Option[Boolean],
    vegan: Option[Boolean],
    tags: Option[Seq[String]],
)

object Ingredient {
  implicit val encoder: Encoder[Ingredient] =
    deriveEncoder[Ingredient]
  implicit val decoder: Decoder[Ingredient] =
    deriveDecoder[Ingredient]
}

object IngredientInput {
  implicit val encoder: Encoder[IngredientInput] =
    deriveEncoder[IngredientInput]
  implicit val decoder: Decoder[IngredientInput] =
    deriveDecoder[IngredientInput]
}

object IngredientUpdateInput {
  implicit val encoder: Encoder[IngredientUpdateInput] =
    deriveEncoder[IngredientUpdateInput]
  implicit val decoder: Decoder[IngredientUpdateInput] =
    deriveDecoder[IngredientUpdateInput]
}
