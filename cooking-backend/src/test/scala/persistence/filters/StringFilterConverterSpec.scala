package persistence.filters

import domain.filters.StringFilter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StringFilterConverterSpec extends AnyFlatSpec with Matchers {

  it should "convert a filter with equals clause" in {
    val filter = StringFilter(
      equals = Some("test"),
      anyOf = None,
      contains = None,
      startsWith = None,
      endsWith = None
    )

    val result = StringFilterConverter.toCypher(filter, "name", "n")

    result shouldBe "n.name = 'test'"
  }

  it should "convert a filter with anyOf clause" in {
    val filter = StringFilter(
      equals = None,
      anyOf = Some(Seq("test1", "test2")),
      contains = None,
      startsWith = None,
      endsWith = None
    )

    val result = StringFilterConverter.toCypher(filter, "name", "n")

    RemoveSpaces(result) shouldBe "n.name IN [ \"test1\", \"test2\"]"
  }

  it should "convert a filter with contains clause" in {
    val filter = StringFilter(
      equals = None,
      anyOf = None,
      contains = Some("test"),
      startsWith = None,
      endsWith = None
    )

    val result = StringFilterConverter.toCypher(filter, "name", "n")

    result shouldBe "n.name CONTAINS 'test'"
  }

  it should "convert a filter with startsWith clause" in {
    val filter = StringFilter(
      equals = None,
      anyOf = None,
      contains = None,
      startsWith = Some("test"),
      endsWith = None
    )

    val result = StringFilterConverter.toCypher(filter, "name", "n")

    result shouldBe "n.name STARTS WITH 'test'"
  }

  it should "convert a filter with endsWith clause" in {
    val filter = StringFilter(
      equals = None,
      anyOf = None,
      contains = None,
      startsWith = None,
      endsWith = Some("test")
    )

    val result = StringFilterConverter.toCypher(filter, "name", "n")

    result shouldBe "n.name ENDS WITH 'test'"
  }

  it should "combine multiple clauses with AND" in {
    val filter = StringFilter(
      equals = Some("test"),
      anyOf = None,
      contains = Some("content"),
      startsWith = None,
      endsWith = None
    )

    val result = StringFilterConverter.toCypher(filter, "name", "n")

    result shouldBe "n.name = 'test' AND n.name CONTAINS 'content'"
  }

  it should "handle all clauses together" in {
    val filter = StringFilter(
      equals = Some("test"),
      anyOf = Some(Seq("test1", "test2")),
      contains = Some("content"),
      startsWith = Some("start"),
      endsWith = Some("end")
    )

    val result = StringFilterConverter.toCypher(filter, "name", "n")

    RemoveSpaces(
      result
    ) shouldBe "n.name = 'test' AND n.name IN [ \"test1\", \"test2\"] AND n.name CONTAINS 'content' AND n.name STARTS WITH 'start' AND n.name ENDS WITH 'end'"
  }

  it should "return empty string for empty filter" in {
    val filter = StringFilter(
      equals = None,
      anyOf = None,
      contains = None,
      startsWith = None,
      endsWith = None
    )

    val result = StringFilterConverter.toCypher(filter, "name", "n")

    result shouldBe ""
  }
}
