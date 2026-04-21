package persistence.filters

import domain.filters.{Filters, NumberFilter, OrderBy, StringFilter}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import scala.jdk.CollectionConverters.SeqHasAsJava

class FiltersConverterSpec extends AnyFlatSpec with Matchers {

  it should "convert a filter with id" in {
    val id = UUID.randomUUID()
    val filters = Filters.empty().copy(id = Some(id))

    val result = FiltersConverter.toCypher(filters, "n")

    result.cypher shouldBe "MATCH (n) WHERE  n.id = $n_id"
    result.params shouldBe Map("n_id" -> id.toString)
  }

  it should "convert a filter with ids" in {
    val id1 = UUID.randomUUID()
    val id2 = UUID.randomUUID()
    val filters = Filters.empty().copy(ids = Some(List(id1, id2)))

    val result = FiltersConverter.toCypher(filters, "n")

    result.cypher should (
      include(s"MATCH (n) WHERE") and
        include("n.id IN $n_ids")
    )
    result.params shouldBe Map("n_ids" -> List(id1.toString, id2.toString).asJava)
  }

  it should "convert a filter with name" in {
    val nameFilter = StringFilter.empty().copy(equals = Some("test"))
    val filters = Filters.empty().copy(name = Some(nameFilter))

    val result = FiltersConverter.toCypher(filters, "n")

    result.cypher shouldBe "MATCH (n) WHERE  n.lowername = $n_name_equals"
    result.params shouldBe Map("n_name_equals" -> "test")
  }

  it should "convert a filter with email" in {
    val emailFilter =
      StringFilter.empty().copy(equals = Some("test@example.com"))
    val filters = Filters.empty().copy(email = Some(emailFilter))
    val result = FiltersConverter.toCypher(filters, "n")

    result.cypher shouldBe "MATCH (n) WHERE  n.loweremail = $n_email_equals"
    result.params shouldBe Map("n_email_equals" -> "test@example.com")
  }

  it should "convert a filter with prepTime" in {
    val prepTimeFilter = NumberFilter(
      greaterOrEqual = Some(10),
      lessOrEqual = Some(20)
    )

    val filters = Filters.empty().copy(prepTime = Some(prepTimeFilter))

    val result = FiltersConverter.toCypher(filters, "n")

    result.cypher shouldBe "MATCH (n) WHERE  n.prepTime >= $n_prepTime_greaterOrEqual AND n.prepTime <= $n_prepTime_lessOrEqual"
    result.params shouldBe Map(
      "n_prepTime_greaterOrEqual" -> Int.box(10),
      "n_prepTime_lessOrEqual" -> Int.box(20)
    )
  }

  it should "convert a filter with cookTime" in {
    val cookTimeFilter = NumberFilter(
      greaterOrEqual = Some(30),
      lessOrEqual = Some(40)
    )

    val filters = Filters.empty().copy(cookTime = Some(cookTimeFilter))

    val result = FiltersConverter.toCypher(filters, "n")

    result.cypher shouldBe "MATCH (n) WHERE  n.cookTime >= $n_cookTime_greaterOrEqual AND n.cookTime <= $n_cookTime_lessOrEqual"
    result.params shouldBe Map(
      "n_cookTime_greaterOrEqual" -> Int.box(30),
      "n_cookTime_lessOrEqual" -> Int.box(40)
    )
  }

  it should "ignore public filter in always-public model" in {
    val filters = Filters.empty().copy(public = Some(true))

    val result = FiltersConverter.toCypher(filters, "n")

    result shouldBe CypherFragment.empty
  }

  it should "convert a filter with tags" in {
    val filters = Filters.empty().copy(tags = Some(List("Italian", "Pasta")))

    val result = FiltersConverter.toCypher(filters, "n")

    result.cypher should (
      include("MATCH (n)-[:HAS_TAG]->(tagFilter0:Tag {lowername: $n_tag_0})") and
        include("MATCH (n)-[:HAS_TAG]->(tagFilter1:Tag {lowername: $n_tag_1})")
    )
    result.params shouldBe Map("n_tag_0" -> "italian", "n_tag_1" -> "pasta")
  }

  it should "convert a filter with ingredients" in {
    val filters =
      Filters.empty().copy(ingredients = Some(List("Tomato", "Cheese")))

    val result = FiltersConverter.toCypher(filters, "n")

    result.cypher should (
      include("MATCH (targetIngredient0:Ingredient {lowername: $n_ingredient_0})") and
        include("MATCH (targetIngredient1:Ingredient {lowername: $n_ingredient_1})") and
        include("OPTIONAL MATCH (targetIngredient0)-[:SUBSTITUTE]-(substituteIngredient0:Ingredient)") and
        include("OPTIONAL MATCH (targetIngredient1)-[:SUBSTITUTE]-(substituteIngredient1:Ingredient)") and
        include("MATCH (n)-[:HAS_INGREDIENT]->(recipeIngredient0:Ingredient)") and
        include("MATCH (n)-[:HAS_INGREDIENT]->(recipeIngredient1:Ingredient)")
    )
    result.params shouldBe Map("n_ingredient_0" -> "tomato", "n_ingredient_1" -> "cheese")
  }

  it should "convert a filter with notIngredients" in {
    val filters =
      Filters.empty().copy(notIngredients = Some(List("Meat", "Fish")))

    val result = FiltersConverter.toCypher(filters, "n")

    result.cypher should (
      include(
        "MATCH (n) WHERE NOT (n)-[:HAS_INGREDIENT]->(:Ingredient {lowername: $n_not_ingredient_0})"
      ) and
        include(
          "MATCH (n) WHERE NOT (n)-[:HAS_INGREDIENT]->(:Ingredient {lowername: $n_not_ingredient_1})"
        )
    )
    result.params shouldBe Map(
      "n_not_ingredient_0" -> "meat",
      "n_not_ingredient_1" -> "fish"
    )
  }

  it should "convert a filter with belongsToUser" in {
    val userId = UUID.randomUUID()
    val filters = Filters.empty().copy(belongsToUser = Some(userId))

    val result = FiltersConverter.toCypher(filters, "n")

    result.cypher should include(
      "MATCH (n)-[:BELONGS_TO|CREATED_BY]->(belongsToUser:User) WHERE belongsToUser.id = $n_belongs_to_user"
    )
    result.params shouldBe Map("n_belongs_to_user" -> userId.toString)
  }

  it should "convert a filter with savedByUser" in {
    val userId = UUID.randomUUID()
    val filters = Filters.empty().copy(savedByUser = Some(userId))

    val result = FiltersConverter.toCypher(filters, "n")

    result.cypher should include(
      "MATCH (n)-[:SAVED_BY]->(savedUser:User) WHERE savedUser.id = $n_saved_by_user"
    )
    result.params shouldBe Map("n_saved_by_user" -> userId.toString)
  }

  it should "combine multiple filters" in {
    val nameFilter = StringFilter.empty().copy(equals = Some("test"))

    val prepTimeFilter = NumberFilter(
      greaterOrEqual = Some(10),
      lessOrEqual = Some(20)
    )

    val filters = Filters
      .empty()
      .copy(
        name = Some(nameFilter),
        prepTime = Some(prepTimeFilter)
      )

    val result = FiltersConverter.toCypher(filters, "n")

    result.cypher should (
      include("n.lowername = $n_name_equals") and
        include("n.prepTime >= $n_prepTime_greaterOrEqual AND n.prepTime <= $n_prepTime_lessOrEqual")
    )
    result.params shouldBe Map(
      "n_name_equals" -> "test",
      "n_prepTime_greaterOrEqual" -> Int.box(10),
      "n_prepTime_lessOrEqual" -> Int.box(20)
    )
  }

  it should "return empty string when no orderBy is specified" in {
    val filters = Filters.empty()

    val result = CypherFragment.getOrderLine(filters, "n")

    result shouldBe ""
  }

  it should "return ORDER BY name when orderBy.name is true" in {
    val filters =
      Filters.empty().copy(orderBy = Some(OrderBy(name = Some(true))))

    val result = CypherFragment.getOrderLine(filters, "n")

    result shouldBe "ORDER BY n.name"
  }

  it should "return ORDER BY name when orderBy.name is false" in {
    val filters =
      Filters.empty().copy(orderBy = Some(OrderBy(name = Some(false))))

    val result = CypherFragment.getOrderLine(filters, "n")

    result shouldBe "ORDER BY n.name"
  }

  it should "prioritize score ordering over name ordering when similarity is active" in {
    val userId = UUID.randomUUID()
    val filters = Filters
      .empty()
      .copy(
        analyzedUser = Some(userId),
        ingredientSimilarity =
          Some(domain.filters.SimilarityFilter(1.0, 0.0, 0.0, 0.0)),
        orderBy = Some(OrderBy(name = Some(true)))
      )

    val result = CypherFragment.getOrderLine(filters, "recipe")

    result shouldBe "ORDER BY score DESC"
  }

  it should "not reference coSaveScore in user-to-recipe mode" in {
    val userId = UUID.randomUUID()
    val filters = Filters
      .empty()
      .copy(
        analyzedUser = Some(userId),
        ingredientSimilarity =
          Some(domain.filters.SimilarityFilter(1.0, 0.0, 0.0, 0.0)),
        coSaveSimilarity =
          Some(domain.filters.SimilarityFilter(1.0, 0.0, 0.0, 0.0)),
        tagSimilarity =
          Some(domain.filters.SimilarityFilter(1.0, 0.0, 0.0, 0.0))
      )

    val result = FiltersConverter.toCypher(filters, "recipe")

    result.cypher should include("ingredientScore")
    result.cypher should include("tagScore")
    result.cypher should not include "coSaveScore"
  }
}
