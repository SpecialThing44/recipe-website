package persistence.filters

import domain.filters.StringFilter
import io.circe.syntax.EncoderOps

object StringFilterConverter extends Cypher[StringFilter] {
  override def toCypher(
      filter: StringFilter,
      property: String,
      nodeVar: String
  ): String = {

    val equalsClause =
      filter.equals.map(value => s"$nodeVar.$property = '$value'")
    val anyOfClause =
      filter.anyOf.map(values => s"$nodeVar.$property IN ${values.asJson}")
    val containsClause =
      filter.contains.map(value => s"$nodeVar.$property CONTAINS '$value'")
    val startsWithClause =
      filter.startsWith.map(value => s"$nodeVar.$property STARTS WITH '$value'")
    val endsWithClause =
      filter.endsWith.map(value => s"$nodeVar.$property ENDS WITH '$value'")
    Seq(
      equalsClause,
      anyOfClause,
      containsClause,
      startsWithClause,
      endsWithClause
    ).filter(_.isDefined).map(_.get).mkString(" AND ")
  }
}
