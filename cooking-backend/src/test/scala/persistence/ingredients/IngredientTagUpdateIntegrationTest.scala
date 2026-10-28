package persistence.ingredients

import domain.ingredients.{IngredientInput, IngredientUpdateInput}
import integration.{IntegrationTestFramework, TestAppHolder}
import persistence.neo4j.Database
import zio.{Runtime, Unsafe}

import java.util.UUID

class IngredientTagUpdateIntegrationTest extends IntegrationTestFramework {
  private val database = TestAppHolder.application.injector.instanceOf[Database]

  private def tagRelationshipCount(ingredientId: UUID): Long =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          database.readTransaction(
            "MATCH (:Ingredient {id: $ingredientId})-[tag:HAS_TAG]->() RETURN count(tag) AS count",
            Map("ingredientId" -> ingredientId.toString),
            _.single().get("count").asLong()
          )
        )
        .getOrThrow()
    }

  it should "replace tags once per ingredient across repeated updates" in {
    val user = createTestAdminUser(standardUserInput)
    login(user.id)
    val ingredient = createTestIngredient(
      IngredientInput(
        name = "Tag Update Ingredient",
        aliases = Seq.empty,
        wikiLink = "https://en.wikipedia.org/wiki/Tomato",
        tags = Seq("original-one", "original-two", "original-three")
      )
    )
    val newTags = Seq("replacement-one", "replacement-two")
    val update = IngredientUpdateInput(None, None, None, Some(newTags))

    val updated = (1 to 3).foldLeft(ingredient) { (current, _) =>
      val next = updateIngredient(current, update)
      next.tags.toSet shouldBe newTags.toSet
      tagRelationshipCount(next.id) shouldBe 2L
      next
    }

    updateIngredient(
      updated,
      update.copy(tags = Some(Seq.empty))
    ).tags shouldBe empty
    tagRelationshipCount(updated.id) shouldBe 0L
  }
}
