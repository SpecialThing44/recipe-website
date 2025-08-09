package domain.ingredients

import domain.shared.Wikified
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class Ingredient(
    name: String,
    aliases: Seq[String],
    wikiLink: String, // Make want to strongly type this
    vegetarian: Boolean,
    vegan: Boolean,
    tags: Seq[String], // Strongly type this ,//
    category: String
) extends Wikified

object Ingredient {
  implicit val encoder: Encoder[Ingredient] =
    deriveEncoder[Ingredient]
  implicit val decoder: Decoder[Ingredient] =
    deriveDecoder[Ingredient]
}
