package persistence.filters

import domain.filters.NumberFilter

object NumberFilterConverter extends Cypher[NumberFilter] {
  override def toCypher(
      filter: NumberFilter,
      property: String,
      nodeVar: String,
      paramPrefix: String
  ): CypherFragment = {
    val greaterParam = s"${paramPrefix}_greaterOrEqual"
    val lessParam = s"${paramPrefix}_lessOrEqual"

    val clauses = Seq(
      filter.greaterOrEqual.map(_ => s"$nodeVar.$property >= $$${greaterParam}"),
      filter.lessOrEqual.map(_ => s"$nodeVar.$property <= $$${lessParam}"),
    ).flatten

    val params = Seq(
      filter.greaterOrEqual.map(value => greaterParam -> Int.box(value)),
      filter.lessOrEqual.map(value => lessParam -> Int.box(value)),
    ).flatten.toMap

    CypherFragment(clauses.mkString(" AND "), params)
  }
}
