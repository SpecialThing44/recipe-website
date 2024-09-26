package domain.food.recipes

import domain.food.ingredients.InstructionIngredient
import domain.people.users.User
import domain.shared.{Identified, Wikified}
import io.circe._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import java.time.Instant
import java.util.UUID

case class Recipe( // Maybe want a double reference back to user, at least in DB for search speed
    name: String,
    user: User,
    aliases: Seq[String],
    // image: Image, // Thumbnail
    tags: Seq[String], // Strongly type this ,//
    ingredients: Seq[InstructionIngredient], // Instruction Ingredient
    prepTime: Int,
    cookTime: Int,
    vegetarian: Boolean,
    vegan: Boolean,
    countryOfOrigin: Option[String], // Type!
    cuisine: Option[String], // Type!
    public: Boolean,
    wikiLink: String, // May want to strongly type this
    videoLink: String, // May want to strongly type this
    instructions: String, // Rich text, need to find a way to resolve image paths likely
    createdOn: Option[Instant] = None,
    updatedOn: Option[Instant] = None,
    id: UUID
) extends Identified
    with Wikified

object Recipe {
  implicit val encoder: Encoder[Recipe] = deriveEncoder[Recipe]
  implicit val decoder: Decoder[Recipe] = deriveDecoder[Recipe]
}
