package persistence.tags

import domain.filters.Filters
import domain.ingredients.{IngredientInput, IngredientUpdateInput, Quantity, Unit as IngredientUnit}
import domain.recipes.{RecipeIngredientInput, RecipeInput, RecipeUpdateInput}
import domain.users.UserInput
import integration.{IntegrationTestFramework, TestAppHolder}
import persistence.neo4j.Database
import zio.{Runtime, Unsafe}

import java.util.UUID

class TagCasingIntegrationTest extends IntegrationTestFramework {
  private val database = TestAppHolder.application.injector.instanceOf[Database]

  private def tagRelationshipCount(entityId: UUID): Long =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(
        database.readTransaction(
          "MATCH (entity {id: $entityId})-[tag:HAS_TAG]->() RETURN count(tag) AS count",
          Map("entityId" -> entityId.toString),
          _.single().get("count").asLong()
        )
      ).getOrThrow()
    }

  private def recipeInput(ingredientId: UUID, tags: Seq[String]): RecipeInput =
    RecipeInput(
      name = "Tag Casing Recipe",
      tags = tags,
      ingredients = Seq(RecipeIngredientInput(
        ingredientId,
        Quantity(IngredientUnit("gram", false, ""), 100)
      )),
      prepTime = 1,
      cookTime = 1,
      countryOfOrigin = None,
      wikiLink = None,
      instructions = quillDelta("Cook")
    )

  it should "reuse canonical tags across recipe and ingredient creation and updates" in {
    val admin = createTestAdminUser(standardUserInput)
    login(admin.id)
    val input = IngredientInput(
      name = "Tag Casing Ingredient",
      aliases = Seq.empty,
      wikiLink = "https://en.wikipedia.org/wiki/Tomato",
      tags = Seq("AuditFresh")
    )
    val ingredient = createTestIngredient(input)
    val variants = Seq("auditfresh", "AUDITFRESH")
    val recipe = createTestRecipe(recipeInput(ingredient.id, variants))
    recipe.tags shouldBe Seq("AuditFresh")
    tagRelationshipCount(recipe.id) shouldBe 1L

    val updatedRecipe = updateRecipe(recipe, RecipeUpdateInput(tags = Some(variants.reverse)))
    updatedRecipe.tags shouldBe Seq("AuditFresh")
    tagRelationshipCount(recipe.id) shouldBe 1L

    val otherIngredient = createTestIngredient(input.copy(name = "Other Tag Casing Ingredient", tags = variants))
    otherIngredient.tags shouldBe Seq("AuditFresh")
    tagRelationshipCount(otherIngredient.id) shouldBe 1L

    val updatedIngredient = updateIngredient(otherIngredient, IngredientUpdateInput(None, None, None, Some(variants.reverse)))
    updatedIngredient.tags shouldBe Seq("AuditFresh")
    tagRelationshipCount(otherIngredient.id) shouldBe 1L
    listRecipes(Filters.empty().copy(ids = Some(List(recipe.id)), tags = Some(Seq("AuDiTfReSh")))).map(_.id) shouldBe Seq(recipe.id)
  }

  it should "allow non-admin users to reuse existing tags with different casing" in {
    val admin = createTestAdminUser(standardUserInput)
    login(admin.id)
    val ingredient = createTestIngredient(IngredientInput(
      name = "Existing Tag Ingredient",
      aliases = Seq.empty,
      wikiLink = "https://en.wikipedia.org/wiki/Tomato",
      tags = Seq("AuditExisting")
    ))
    val user = createTestUser(UserInput("Tag User", "tag-user@example.com"))
    login(user.id)

    val recipe = createTestRecipe(recipeInput(ingredient.id, Seq("auditexisting", "AUDITEXISTING")))

    recipe.tags shouldBe Seq("AuditExisting")
    tagRelationshipCount(recipe.id) shouldBe 1L
  }
}
