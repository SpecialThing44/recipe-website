package persistence.filters

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import domain.filters.{Filters, StringFilter, NumberFilter}
import java.util.UUID

class FiltersConverterSpec extends AnyFlatSpec with Matchers {

  it should "convert a filter with id" in {
    val id = UUID.randomUUID()
    val filters = Filters(
      id = Some(id),
      ids = None,
      belongsToUser = None,
      savedByUser = None,
      name = None,
      email = None,
      prepTime = None,
      cookTime = None,
      vegetarian = None,
      vegan = None,
      public = None,
      tags = None,
      ingredients = None,
      notIngredients = None
    )

    val result = FiltersConverter.toCypher(filters, "n")

    result shouldBe s"MATCH (n) WHERE  n.id = '$id'"
  }

  it should "convert a filter with ids" in {
    val id1 = UUID.randomUUID()
    val id2 = UUID.randomUUID()
    val filters = Filters(
      id = None,
      ids = Some(List(id1, id2)),
      belongsToUser = None,
      savedByUser = None,
      name = None,
      email = None,
      prepTime = None,
      cookTime = None,
      vegetarian = None,
      vegan = None,
      public = None,
      tags = None,
      ingredients = None,
      notIngredients = None
    )

    val result = FiltersConverter.toCypher(filters, "n")

    result should (
      include(s"MATCH (n) WHERE") and
      include(s"n.id IN") and
      include(id1.toString) and
      include(id2.toString)
    )
  }

  it should "convert a filter with name" in {
    val nameFilter = StringFilter(
      equals = Some("test"),
      anyOf = None,
      contains = None,
      startsWith = None,
      endsWith = None
    )

    val filters = Filters(
      id = None,
      ids = None,
      belongsToUser = None,
      savedByUser = None,
      name = Some(nameFilter),
      email = None,
      prepTime = None,
      cookTime = None,
      vegetarian = None,
      vegan = None,
      public = None,
      tags = None,
      ingredients = None,
      notIngredients = None
    )

    val result = FiltersConverter.toCypher(filters, "n")

    result shouldBe "MATCH (n) WHERE  n.lowername = 'test'"
  }

  it should "convert a filter with email" in {
    val emailFilter = StringFilter(
      equals = Some("test@example.com"),
      anyOf = None,
      contains = None,
      startsWith = None,
      endsWith = None
    )

    val filters = Filters(
      id = None,
      ids = None,
      belongsToUser = None,
      savedByUser = None,
      name = None,
      email = Some(emailFilter),
      prepTime = None,
      cookTime = None,
      vegetarian = None,
      vegan = None,
      public = None,
      tags = None,
      ingredients = None,
      notIngredients = None
    )

    val result = FiltersConverter.toCypher(filters, "n")

    result shouldBe "MATCH (n) WHERE  n.loweremail = 'test@example.com'"
  }

  it should "convert a filter with prepTime" in {
    val prepTimeFilter = NumberFilter(
      greaterOrEqual = Some(10),
      lessOrEqual = Some(20)
    )

    val filters = Filters(
      id = None,
      ids = None,
      belongsToUser = None,
      savedByUser = None,
      name = None,
      email = None,
      prepTime = Some(prepTimeFilter),
      cookTime = None,
      vegetarian = None,
      vegan = None,
      public = None,
      tags = None,
      ingredients = None,
      notIngredients = None
    )

    val result = FiltersConverter.toCypher(filters, "n")

    result shouldBe "MATCH (n) WHERE  n.prepTime >= 10 AND n.prepTime <= 20"
  }

  it should "convert a filter with cookTime" in {
    val cookTimeFilter = NumberFilter(
      greaterOrEqual = Some(30),
      lessOrEqual = Some(40)
    )

    val filters = Filters(
      id = None,
      ids = None,
      belongsToUser = None,
      savedByUser = None,
      name = None,
      email = None,
      prepTime = None,
      cookTime = Some(cookTimeFilter),
      vegetarian = None,
      vegan = None,
      public = None,
      tags = None,
      ingredients = None,
      notIngredients = None
    )

    val result = FiltersConverter.toCypher(filters, "n")

    result shouldBe "MATCH (n) WHERE  n.cookTime >= 30 AND n.cookTime <= 40"
  }

  it should "convert a filter with vegetarian" in {
    val filters = Filters(
      id = None,
      ids = None,
      belongsToUser = None,
      savedByUser = None,
      name = None,
      email = None,
      prepTime = None,
      cookTime = None,
      vegetarian = Some(true),
      vegan = None,
      public = None,
      tags = None,
      ingredients = None,
      notIngredients = None
    )

    val result = FiltersConverter.toCypher(filters, "n")

    result shouldBe "MATCH (n) WHERE  n.vegetarian = true"
  }

  it should "convert a filter with vegan" in {
    val filters = Filters(
      id = None,
      ids = None,
      belongsToUser = None,
      savedByUser = None,
      name = None,
      email = None,
      prepTime = None,
      cookTime = None,
      vegetarian = None,
      vegan = Some(true),
      public = None,
      tags = None,
      ingredients = None,
      notIngredients = None
    )

    val result = FiltersConverter.toCypher(filters, "n")

    result shouldBe "MATCH (n) WHERE  n.vegan = true"
  }

  it should "convert a filter with public" in {
    val filters = Filters(
      id = None,
      ids = None,
      belongsToUser = None,
      savedByUser = None,
      name = None,
      email = None,
      prepTime = None,
      cookTime = None,
      vegetarian = None,
      vegan = None,
      public = Some(true),
      tags = None,
      ingredients = None,
      notIngredients = None
    )

    val result = FiltersConverter.toCypher(filters, "n")

    result shouldBe "MATCH (n) WHERE  n.public = true"
  }

  it should "convert a filter with tags" in {
    val filters = Filters(
      id = None,
      ids = None,
      belongsToUser = None,
      savedByUser = None,
      name = None,
      email = None,
      prepTime = None,
      cookTime = None,
      vegetarian = None,
      vegan = None,
      public = None,
      tags = Some(List("Italian", "Pasta")),
      ingredients = None,
      notIngredients = None
    )

    val result = FiltersConverter.toCypher(filters, "n")

    result should (
      include("MATCH (n)-[:HAS_TAG]->(tag:Italian:Tag)") and
      include("MATCH (n)-[:HAS_TAG]->(tag:Pasta:Tag)")
    )
  }

  it should "convert a filter with ingredients" in {
    val filters = Filters(
      id = None,
      ids = None,
      belongsToUser = None,
      savedByUser = None,
      name = None,
      email = None,
      prepTime = None,
      cookTime = None,
      vegetarian = None,
      vegan = None,
      public = None,
      tags = None,
      ingredients = Some(List("Tomato", "Cheese")),
      notIngredients = None
    )

    val result = FiltersConverter.toCypher(filters, "n")

    result should (
      include("MATCH (n)-[:HAS_INGREDIENT]->(ingredient:Tomato:Ingredient)") and
      include("MATCH (n)-[:HAS_INGREDIENT]->(ingredient:Cheese:Ingredient)")
    )
  }

  it should "convert a filter with notIngredients" in {
    val filters = Filters(
      id = None,
      ids = None,
      belongsToUser = None,
      savedByUser = None,
      name = None,
      email = None,
      prepTime = None,
      cookTime = None,
      vegetarian = None,
      vegan = None,
      public = None,
      tags = None,
      ingredients = None,
      notIngredients = Some(List("Meat", "Fish"))
    )

    val result = FiltersConverter.toCypher(filters, "n")

    result should (
      include("MATCH (n) WHERE NOT (n)-[:HAS_INGREDIENT]->(notIngredient:Meat:Ingredient)") and
      include("MATCH (n) WHERE NOT (n)-[:HAS_INGREDIENT]->(notIngredient:Fish:Ingredient)")
    )
  }

  it should "convert a filter with belongsToUser" in {
    val userId = UUID.randomUUID()
    val filters = Filters(
      id = None,
      ids = None,
      belongsToUser = Some(userId),
      savedByUser = None,
      name = None,
      email = None,
      prepTime = None,
      cookTime = None,
      vegetarian = None,
      vegan = None,
      public = None,
      tags = None,
      ingredients = None,
      notIngredients = None
    )

    val result = FiltersConverter.toCypher(filters, "n")

    result should include(s"MATCH (n)-[:BELONGS_TO]->(user:User) WHERE user.id = $userId")
  }

  it should "convert a filter with savedByUser" in {
    val userId = UUID.randomUUID()
    val filters = Filters(
      id = None,
      ids = None,
      belongsToUser = None,
      savedByUser = Some(userId),
      name = None,
      email = None,
      prepTime = None,
      cookTime = None,
      vegetarian = None,
      vegan = None,
      public = None,
      tags = None,
      ingredients = None,
      notIngredients = None
    )

    val result = FiltersConverter.toCypher(filters, "n")

    result should include(s"MATCH (n)-[:SAVED_BY]->(user:User) WHERE user.id = $userId")
  }

  it should "combine multiple filters" in {
    val nameFilter = StringFilter(
      equals = Some("test"),
      anyOf = None,
      contains = None,
      startsWith = None,
      endsWith = None
    )

    val prepTimeFilter = NumberFilter(
      greaterOrEqual = Some(10),
      lessOrEqual = Some(20)
    )

    val filters = Filters(
      id = None,
      ids = None,
      belongsToUser = None,
      savedByUser = None,
      name = Some(nameFilter),
      email = None,
      prepTime = Some(prepTimeFilter),
      cookTime = None,
      vegetarian = Some(true),
      vegan = None,
      public = None,
      tags = None,
      ingredients = None,
      notIngredients = None
    )

    val result = FiltersConverter.toCypher(filters, "n")

    result should (
      include("n.lowername = 'test'") and
      include("n.prepTime >= 10 AND n.prepTime <= 20") and
      include("n.vegetarian = true")
    )
  }
}
