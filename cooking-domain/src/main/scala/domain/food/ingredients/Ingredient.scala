package domain.food.ingredients

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import java.net.URL

case class Ingredient(
    name: String,
    aliases: Seq[String],
    wikiLink: URL, // Make want to strongly type this
    vegetarian: Boolean,
    vegan: Boolean,
    tags: Seq[String], // Strongly type this ,//
    category: String
)

object Ingredient {
  implicit val encoder: Encoder[Ingredient] = deriveEncoder[Ingredient]
  implicit val decoder: Decoder[Ingredient] = deriveDecoder[Ingredient]
}
