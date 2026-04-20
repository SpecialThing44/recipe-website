package persistence.filters

import domain.filters.StringFilter

import scala.jdk.CollectionConverters.SeqHasAsJava

object StringFilterConverter extends Cypher[StringFilter] {
  override def toCypher(
      filter: StringFilter,
      property: String,
      nodeVar: String,
      paramPrefix: String
  ): CypherFragment = {
    val sanitized = filter.sanitize

    val equalsParam = s"${paramPrefix}_equals"
    val anyOfParam = s"${paramPrefix}_anyOf"
    val containsParam = s"${paramPrefix}_contains"
    val startsWithParam = s"${paramPrefix}_startsWith"
    val endsWithParam = s"${paramPrefix}_endsWith"

    val clauses = Seq(
      sanitized.equals.map(_ => s"$nodeVar.$property = $$${equalsParam}"),
      sanitized.anyOf.map(_ => s"$nodeVar.$property IN $$${anyOfParam}"),
      sanitized.contains.map(_ => s"$nodeVar.$property CONTAINS $$${containsParam}"),
      sanitized.startsWith.map(_ =>
        s"$nodeVar.$property STARTS WITH $$${startsWithParam}"
      ),
      sanitized.endsWith.map(_ => s"$nodeVar.$property ENDS WITH $$${endsWithParam}")
    ).flatten

    val params = Seq(
      sanitized.equals.map(value => equalsParam -> value),
      sanitized.anyOf.map(values => anyOfParam -> values.asJava),
      sanitized.contains.map(value => containsParam -> value),
      sanitized.startsWith.map(value => startsWithParam -> value),
      sanitized.endsWith.map(value => endsWithParam -> value)
    ).flatten.toMap

    CypherFragment(clauses.mkString(" AND "), params)
  }
}
