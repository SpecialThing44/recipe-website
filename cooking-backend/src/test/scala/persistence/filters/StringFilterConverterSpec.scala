package persistence.filters

import domain.filters.StringFilter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters.SeqHasAsJava

class StringFilterConverterSpec extends AnyFlatSpec with Matchers {

  it should "convert a filter with equals clause" in {
    val filter = StringFilter(
      equals = Some("test"),
      anyOf = None,
      contains = None,
      startsWith = None,
      endsWith = None
    )

    val result = StringFilterConverter.toCypher(filter, "name", "n", "name")

    result.cypher shouldBe "n.name = $name_equals"
    result.params shouldBe Map("name_equals" -> "test")
  }

  it should "convert a filter with anyOf clause" in {
    val filter = StringFilter(
      equals = None,
      anyOf = Some(Seq("test1", "test2")),
      contains = None,
      startsWith = None,
      endsWith = None
    )

    val result = StringFilterConverter.toCypher(filter, "name", "n", "name")

    result.cypher shouldBe "n.name IN $name_anyOf"
    result.params shouldBe Map("name_anyOf" -> Seq("test1", "test2").asJava)
  }

  it should "convert a filter with contains clause" in {
    val filter = StringFilter(
      equals = None,
      anyOf = None,
      contains = Some("test"),
      startsWith = None,
      endsWith = None
    )

    val result = StringFilterConverter.toCypher(filter, "name", "n", "name")

    result.cypher shouldBe "n.name CONTAINS $name_contains"
    result.params shouldBe Map("name_contains" -> "test")
  }

  it should "convert a filter with startsWith clause" in {
    val filter = StringFilter(
      equals = None,
      anyOf = None,
      contains = None,
      startsWith = Some("test"),
      endsWith = None
    )

    val result = StringFilterConverter.toCypher(filter, "name", "n", "name")

    result.cypher shouldBe "n.name STARTS WITH $name_startsWith"
    result.params shouldBe Map("name_startsWith" -> "test")
  }

  it should "convert a filter with endsWith clause" in {
    val filter = StringFilter(
      equals = None,
      anyOf = None,
      contains = None,
      startsWith = None,
      endsWith = Some("test")
    )

    val result = StringFilterConverter.toCypher(filter, "name", "n", "name")

    result.cypher shouldBe "n.name ENDS WITH $name_endsWith"
    result.params shouldBe Map("name_endsWith" -> "test")
  }

  it should "combine multiple clauses with AND" in {
    val filter = StringFilter(
      equals = Some("test"),
      anyOf = None,
      contains = Some("content"),
      startsWith = None,
      endsWith = None
    )

    val result = StringFilterConverter.toCypher(filter, "name", "n", "name")

    result.cypher shouldBe "n.name = $name_equals AND n.name CONTAINS $name_contains"
    result.params shouldBe Map(
      "name_equals" -> "test",
      "name_contains" -> "content"
    )
  }

  it should "handle all clauses together" in {
    val filter = StringFilter(
      equals = Some("test"),
      anyOf = Some(Seq("test1", "test2")),
      contains = Some("content"),
      startsWith = Some("start"),
      endsWith = Some("end")
    )

    val result = StringFilterConverter.toCypher(filter, "name", "n", "name")

    result.cypher shouldBe "n.name = $name_equals AND n.name IN $name_anyOf AND n.name CONTAINS $name_contains AND n.name STARTS WITH $name_startsWith AND n.name ENDS WITH $name_endsWith"
    result.params shouldBe Map(
      "name_equals" -> "test",
      "name_anyOf" -> Seq("test1", "test2").asJava,
      "name_contains" -> "content",
      "name_startsWith" -> "start",
      "name_endsWith" -> "end"
    )
  }

  it should "return empty string for empty filter" in {
    val filter = StringFilter(
      equals = None,
      anyOf = None,
      contains = None,
      startsWith = None,
      endsWith = None
    )

    val result = StringFilterConverter.toCypher(filter, "name", "n", "name")

    result shouldBe CypherFragment.empty
  }
}
