package domain.recipes

import domain.ingredients.InstructionIngredient
import domain.shared.{Identified, Wikified}
import domain.users.User
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
    createdOn: Instant,
    updatedOn: Instant,
    id: UUID
) extends Identified
    with Wikified

case class RecipeInput(
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
    instructions: String,
)

object Recipe {
  implicit val encoder: Encoder[Recipe] = deriveEncoder[Recipe]
  implicit val decoder: Decoder[Recipe] = deriveDecoder[Recipe]
}

object RecipeInput {
  implicit val encoder: Encoder[RecipeInput] = deriveEncoder[RecipeInput]
  implicit val decoder: Decoder[RecipeInput] = deriveDecoder[RecipeInput]
}
