package persistence.filters

import domain.filters.NumberFilter

object NumberFilterConverter extends Cypher[NumberFilter] {
  override def toCypher(
      filter: NumberFilter,
      property: String,
      nodeVar: String
  ): String = {
    val greaterClause =
      filter.greaterOrEqual.map(value => s"$nodeVar.$property >= $value")
    val lessClause =
      filter.lessOrEqual.map(value => s"$nodeVar.$property <= $value")
    Seq(
      greaterClause,
      lessClause,
    ).filter(_.isDefined).map(_.get).mkString(" AND ")
  }
}
