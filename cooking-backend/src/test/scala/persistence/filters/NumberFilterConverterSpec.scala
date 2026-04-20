package persistence.filters

import domain.filters.NumberFilter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class NumberFilterConverterSpec extends AnyFlatSpec with Matchers {

  it should "convert a filter with greaterOrEqual clause" in {
    val filter = NumberFilter(
      greaterOrEqual = Some(10),
      lessOrEqual = None
    )

    val result = NumberFilterConverter.toCypher(filter, "age", "n", "age")

    result.cypher shouldBe "n.age >= $age_greaterOrEqual"
    result.params shouldBe Map("age_greaterOrEqual" -> Int.box(10))
  }

  it should "convert a filter with lessOrEqual clause" in {
    val filter = NumberFilter(
      greaterOrEqual = None,
      lessOrEqual = Some(20)
    )

    val result = NumberFilterConverter.toCypher(filter, "age", "n", "age")

    result.cypher shouldBe "n.age <= $age_lessOrEqual"
    result.params shouldBe Map("age_lessOrEqual" -> Int.box(20))
  }

  it should "combine both clauses with AND" in {
    val filter = NumberFilter(
      greaterOrEqual = Some(10),
      lessOrEqual = Some(20)
    )

    val result = NumberFilterConverter.toCypher(filter, "age", "n", "age")

    result.cypher shouldBe "n.age >= $age_greaterOrEqual AND n.age <= $age_lessOrEqual"
    result.params shouldBe Map(
      "age_greaterOrEqual" -> Int.box(10),
      "age_lessOrEqual" -> Int.box(20)
    )
  }

  it should "return empty string for empty filter" in {
    val filter = NumberFilter(
      greaterOrEqual = None,
      lessOrEqual = None
    )

    val result = NumberFilterConverter.toCypher(filter, "age", "n", "age")

    result shouldBe CypherFragment.empty
  }
}
