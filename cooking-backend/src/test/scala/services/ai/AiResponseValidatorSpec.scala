package services.ai

import api.ai.AiResponseValidator
import domain.ai.{AiParsedIngredient, AiParsedQuantity, AiRecipeParseResponse}
import domain.ingredients.Ingredient
import domain.users.User
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import java.util.UUID

class AiResponseValidatorSpec extends AnyFlatSpec with Matchers {

  private val knownIngredientId: UUID = UUID.randomUUID()

  private val knownIngredient: Ingredient = Ingredient(
    name = "tomato",
    aliases = Seq("roma tomato"),
    wikiLink = "",
    tags = Seq("vegetable"),
    createdBy = User.empty().copy(
      name = "Test User",
      email = "test@example.com",
      identity = "test-user",
      createdOn = Instant.now,
      updatedOn = Instant.now
    ),
    id = knownIngredientId
  )

  private def baseResponseWithIngredient(ingredient: AiParsedIngredient): AiRecipeParseResponse =
    AiRecipeParseResponse(
      name = "Tomato Soup",
      instructions = "Mix and cook.",
      prepTime = Some(10),
      cookTime = Some(20),
      servings = Some(2),
      tags = Seq("soup"),
      ingredients = Seq(ingredient)
    )

  it should "coerce invalid ingredient unit to piece" in {
    val parsedIngredient = AiParsedIngredient(
      rawText = "2 splashes tomato",
      ingredientId = Some(knownIngredientId),
      ingredientName = Some("tomato"),
      quantity = AiParsedQuantity(amount = 2, unit = "splash"),
      description = None
    )

    val parsed = baseResponseWithIngredient(parsedIngredient)

    val result = AiResponseValidator.validateParsedRecipe(parsed, Seq(knownIngredient))

    result match {
      case Right(validated) =>
        validated.ingredients.head.quantity.unit shouldBe "piece"
      case Left(err) =>
        fail(s"Expected valid response but got error: $err")
    }
  }

  it should "reject malformed ingredient entries" in {
    val malformedIngredient = AiParsedIngredient(
      rawText = "",
      ingredientId = Some(knownIngredientId),
      ingredientName = Some("tomato"),
      quantity = AiParsedQuantity(amount = 1, unit = "cup"),
      description = None
    )

    val parsed = baseResponseWithIngredient(malformedIngredient)

    val result = AiResponseValidator.validateParsedRecipe(parsed, Seq(knownIngredient))

    result match {
      case Left(err) =>
        err should include("rawText must be non-empty")
      case Right(validated) =>
        fail(s"Expected malformed validation error but got: $validated")
    }
  }

  it should "enforce id and name consistency by canonicalizing ingredientName from known id" in {
    val inconsistentIngredient = AiParsedIngredient(
      rawText = "1 cup chopped tomato",
      ingredientId = Some(knownIngredientId),
      ingredientName = Some("hallucinated-name"),
      quantity = AiParsedQuantity(amount = 1, unit = "cup"),
      description = None
    )

    val parsed = baseResponseWithIngredient(inconsistentIngredient)

    val result = AiResponseValidator.validateParsedRecipe(parsed, Seq(knownIngredient))

    result match {
      case Right(validated) =>
        validated.ingredients.head.ingredientName shouldBe Some("tomato")
      case Left(err) =>
        fail(s"Expected consistent ingredient result but got error: $err")
    }
  }
}
