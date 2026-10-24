package api.users

import domain.ingredients.Ingredient
import domain.users.User
import io.circe.syntax.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.util.UUID

class PublicUserSerializationSpec extends AnyFlatSpec with Matchers {
  it should "redact author credentials without changing private user serialization" in {
    val author = User.empty().copy(email = "private@example.com", identity = "private-subject")
    val ingredient = Ingredient("Salt", Seq.empty, "", Seq.empty, author, UUID.randomUUID())
    val publicAuthor = ingredient.asJson.hcursor.downField("createdBy")
    publicAuthor.get[String]("email") shouldBe Right("")
    publicAuthor.get[String]("identity") shouldBe Right("")
    author.asJson.hcursor.get[String]("email") shouldBe Right("private@example.com")
  }
  it should "redact recipe authors as well as nested ingredient authors" in {
    val author = User.empty().copy(email = "private@example.com", identity = "private-subject")
    val ingredient = Ingredient("Salt", Seq.empty, "", Seq.empty, author, UUID.randomUUID())
    val recipe = domain.recipes.Recipe("Soup", author, Seq.empty,
      Seq(domain.ingredients.InstructionIngredient(ingredient, domain.ingredients.Quantity(domain.ingredients.Unit.Piece, 1), None)),
      prepTime = 0, cookTime = 0, servings = 1, countryOfOrigin = None, public = true, wikiLink = None,
      instructions = "", createdOn = java.time.Instant.now(), updatedOn = java.time.Instant.now(), id = UUID.randomUUID())
    val json = recipe.asJson.noSpaces
    json should not include "private@example.com"
    json should not include "private-subject"
  }

}
