package persistence.filters

import domain.filters.StringFilter
import io.circe.syntax.EncoderOps

object StringFilterConverter extends Cypher[StringFilter] {
  override def toCypher(
      filter: StringFilter,
      property: String,
      nodeVar: String
  ): String = {
    val sanitized = filter.sanitize

    val equalsClause =
      sanitized.equals.map(value => s"$nodeVar.$property = '$value'")
    val anyOfClause =
      sanitized.anyOf.map(values => s"$nodeVar.$property IN ${values.asJson}")
    val containsClause =
      sanitized.contains.map(value => s"$nodeVar.$property CONTAINS '$value'")
    val startsWithClause =
      sanitized.startsWith.map(value =>
        s"$nodeVar.$property STARTS WITH '$value'"
      )
    val endsWithClause =
      sanitized.endsWith.map(value => s"$nodeVar.$property ENDS WITH '$value'")
    Seq(
      equalsClause,
      anyOfClause,
      containsClause,
      startsWithClause,
      endsWithClause
    ).filter(_.isDefined).map(_.get).mkString(" AND ")
  }
}
