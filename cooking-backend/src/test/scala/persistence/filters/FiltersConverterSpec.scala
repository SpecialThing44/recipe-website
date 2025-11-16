package persistence.filters

import domain.filters.{Filters, NumberFilter, OrderBy, StringFilter}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class FiltersConverterSpec extends AnyFlatSpec with Matchers {

  it should "convert a filter with id" in {
    val id = UUID.randomUUID()
    val filters = Filters.empty().copy(id = Some(id))

    val result = FiltersConverter.toCypher(filters, "n")

    result shouldBe s"MATCH (n) WHERE  n.id = '$id'"
  }

  it should "convert a filter with ids" in {
    val id1 = UUID.randomUUID()
    val id2 = UUID.randomUUID()
    val filters = Filters.empty().copy(ids = Some(List(id1, id2)))

    val result = FiltersConverter.toCypher(filters, "n")

    result should (
      include(s"MATCH (n) WHERE") and
        include(s"n.id IN") and
        include(id1.toString) and
        include(id2.toString)
    )
  }

  it should "convert a filter with name" in {
    val nameFilter = StringFilter.empty().copy(equals = Some("test"))
    val filters = Filters.empty().copy(name = Some(nameFilter))

    val result = FiltersConverter.toCypher(filters, "n")

    result shouldBe "MATCH (n) WHERE  n.lowername = 'test'"
  }

  it should "convert a filter with email" in {
    val emailFilter =
      StringFilter.empty().copy(equals = Some("test@example.com"))
    val filters = Filters.empty().copy(email = Some(emailFilter))
    val result = FiltersConverter.toCypher(filters, "n")

    result shouldBe "MATCH (n) WHERE  n.loweremail = 'test@example.com'"
  }

  it should "convert a filter with prepTime" in {
    val prepTimeFilter = NumberFilter(
      greaterOrEqual = Some(10),
      lessOrEqual = Some(20)
    )

    val filters = Filters.empty().copy(prepTime = Some(prepTimeFilter))

    val result = FiltersConverter.toCypher(filters, "n")

    result shouldBe "MATCH (n) WHERE  n.prepTime >= 10 AND n.prepTime <= 20"
  }

  it should "convert a filter with cookTime" in {
    val cookTimeFilter = NumberFilter(
      greaterOrEqual = Some(30),
      lessOrEqual = Some(40)
    )

    val filters = Filters.empty().copy(cookTime = Some(cookTimeFilter))

    val result = FiltersConverter.toCypher(filters, "n")

    result shouldBe "MATCH (n) WHERE  n.cookTime >= 30 AND n.cookTime <= 40"
  }

  it should "convert a filter with vegetarian" in {
    val filters = Filters.empty().copy(vegetarian = Some(true))

    val result = FiltersConverter.toCypher(filters, "n")

    result shouldBe "MATCH (n) WHERE  n.vegetarian = 'true'"
  }

  it should "convert a filter with vegan" in {
    val filters = Filters.empty().copy(vegan = Some(true))

    val result = FiltersConverter.toCypher(filters, "n")

    result shouldBe "MATCH (n) WHERE  n.vegan = 'true'"
  }

  it should "convert a filter with public" in {
    val filters = Filters.empty().copy(public = Some(true))

    val result = FiltersConverter.toCypher(filters, "n")

    result shouldBe "MATCH (n) WHERE  n.public = true"
  }

  it should "convert a filter with tags" in {
    val filters = Filters.empty().copy(tags = Some(List("Italian", "Pasta")))

    val result = FiltersConverter.toCypher(filters, "n")

    result should (
      include("MATCH (n)-[:HAS_TAG]->(tag:Italian:Tag)") and
        include("MATCH (n)-[:HAS_TAG]->(tag:Pasta:Tag)")
    )
  }

  it should "convert a filter with ingredients" in {
    val filters =
      Filters.empty().copy(ingredients = Some(List("Tomato", "Cheese")))

    val result = FiltersConverter.toCypher(filters, "n")

    result should (
      include("MATCH (n)-[:HAS_INGREDIENT]->(hasIngredient:Ingredient {name: 'Tomato'})") and
        include("MATCH (n)-[:HAS_INGREDIENT]->(hasIngredient:Ingredient {name: 'Cheese'})")
    )
  }

  it should "convert a filter with notIngredients" in {
    val filters =
      Filters.empty().copy(notIngredients = Some(List("Meat", "Fish")))

    val result = FiltersConverter.toCypher(filters, "n")

    result should (
      include(
        "MATCH (n) WHERE NOT (n)-[:HAS_INGREDIENT]->(notIngredient:Ingredient {name: 'Meat'})"
      ) and
        include(
          "MATCH (n) WHERE NOT (n)-[:HAS_INGREDIENT]->(notIngredient:Ingredient {name: 'Fish'})"
        )
    )
  }

  it should "convert a filter with belongsToUser" in {
    val userId = UUID.randomUUID()
    val filters = Filters.empty().copy(belongsToUser = Some(userId))

    val result = FiltersConverter.toCypher(filters, "n")

    result should include(
      s"MATCH (n)-[:BELONGS_TO|CREATED_BY]->(belongsToUser:User) WHERE belongsToUser.id = '$userId'"
    )
  }

  it should "convert a filter with savedByUser" in {
    val userId = UUID.randomUUID()
    val filters = Filters.empty().copy(savedByUser = Some(userId))

    val result = FiltersConverter.toCypher(filters, "n")

    result should include(
      s"MATCH (n)-[:SAVED_BY]->(savedUser:User) WHERE savedUser.id = '$userId'"
    )
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
        prepTime = Some(prepTimeFilter),
        vegetarian = Some(true)
      )

    val result = FiltersConverter.toCypher(filters, "n")

    result should (
      include("n.lowername = 'test'") and
        include("n.prepTime >= 10 AND n.prepTime <= 20") and
        include("n.vegetarian = 'true'")
    )
  }

  it should "return empty string when no orderBy is specified" in {
    val filters = Filters.empty()

    val result = FiltersConverter.getOrderLine(filters, "n")

    result shouldBe ""
  }

  it should "return ORDER BY name when orderBy.name is true" in {
    val filters = Filters.empty().copy(orderBy = Some(OrderBy(name = Some(true))))

    val result = FiltersConverter.getOrderLine(filters, "n")

    result shouldBe "ORDER BY n.name"
  }

  it should "return ORDER BY name when orderBy.name is false" in {
    val filters = Filters.empty().copy(orderBy = Some(OrderBy(name = Some(false))))

    val result = FiltersConverter.getOrderLine(filters, "n")

    result shouldBe "ORDER BY n.name"
  }

  it should "prioritize score ordering over name ordering when similarity is active" in {
    val userId = UUID.randomUUID()
    val filters = Filters.empty().copy(
      analyzedEntity = Some(userId),
      ingredientSimilarity = Some(domain.filters.SimilarityFilter(1.0, 0.0, 0.0, 0.0)),
      orderBy = Some(OrderBy(name = Some(true)))
    )

    val result = FiltersConverter.getOrderLine(filters, "recipe")

    result shouldBe "ORDER BY score DESC"
  }
}

