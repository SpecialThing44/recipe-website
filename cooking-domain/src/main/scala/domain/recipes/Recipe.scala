package domain.recipes

import domain.ingredients.InstructionIngredient
import domain.shared.{Identified, Wikified}
import domain.users.User
import io.circe.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import java.time.Instant
import java.util.UUID

// Reuse AvatarUrls structure for recipe images
type ImageUrls = domain.users.AvatarUrls

case class Recipe(
    name: String,
    createdBy: User,
    tags: Seq[String],
    ingredients: Seq[InstructionIngredient],
    prepTime: Int,
    cookTime: Int,
    servings: Int, // Number of servings (positive integer)
    countryOfOrigin: Option[String],
    public: Boolean,
    wikiLink: Option[String],
    instructions: String, // Quill Delta JSON format
    instructionImages: Seq[String] =
      Seq.empty, // URLs of images embedded in instructions
    image: Option[ImageUrls] = None,
    createdOn: Instant,
    updatedOn: Instant,
    id: UUID
) extends Identified

case class RecipeInput(
    name: String,
    tags: Seq[String],
    ingredients: Seq[RecipeIngredientInput],
    prepTime: Int,
    cookTime: Int,
    servings: Int = 1, // Number of servings (defaults to 1)
    countryOfOrigin: Option[String],
    public: Boolean,
    wikiLink: Option[String],
    instructions: String,
)

case class RecipeUpdateInput(
    name: Option[String] = None,
    tags: Option[Seq[String]] = None,
    ingredients: Option[Seq[RecipeIngredientInput]] = None,
    prepTime: Option[Int] = None,
    cookTime: Option[Int] = None,
    servings: Option[Int] = None,
    countryOfOrigin: Option[String] = None,
    public: Option[Boolean] = None,
    wikiLink: Option[String] = None,
    instructions: Option[String] = None, // Quill Delta JSON format
    instructionImages: Option[Seq[String]] = None,
    image: Option[ImageUrls] = None,
)

object Recipe {
  implicit val encoder: Encoder[Recipe] = deriveEncoder[Recipe]
  implicit val decoder: Decoder[Recipe] = deriveDecoder[Recipe]
}

object RecipeInput {
  implicit val encoder: Encoder[RecipeInput] = deriveEncoder[RecipeInput]
  implicit val decoder: Decoder[RecipeInput] = deriveDecoder[RecipeInput]
}

object RecipeUpdateInput {
  implicit val encoder: Encoder[RecipeUpdateInput] =
    deriveEncoder[RecipeUpdateInput]
  implicit val decoder: Decoder[RecipeUpdateInput] =
    deriveDecoder[RecipeUpdateInput]
}
