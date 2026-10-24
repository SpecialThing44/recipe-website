package api.recipes

import domain.ingredients.{Quantity, Unit as IngredientUnit}
import domain.recipes.{RecipeInput, RecipeComponentInput}
import java.util.UUID
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import zio.{Runtime, Unsafe}

class RecipeValidatorSpec extends AnyFlatSpec with Matchers {
  it should "accept whole recipe and serving components but reject undefined weight conversions" in {
    Seq(IngredientUnit.Piece -> true, IngredientUnit.Serving -> true, IngredientUnit.Gram -> false).foreach { case (unit, valid) =>
      val input = RecipeInput("Soup", Seq.empty, Seq.empty,
        recipeIngredients = Seq(RecipeComponentInput(UUID.randomUUID(), Quantity(unit, 2))),
        prepTime = 0, cookTime = 0, countryOfOrigin = None, wikiLink = None, instructions = "")
      val result = Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.run(RecipeValidator.validateRecipeInput(input).either).getOrThrowFiberFailure()
      }
      result.isRight shouldBe valid
    }
  }
}
