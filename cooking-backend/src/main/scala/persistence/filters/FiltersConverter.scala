package persistence.filters

import domain.filters.Filters
import persistence.filters.CypherSupport.{mergeParams, nonEmpty}
import persistence.filters.base.{
  FiltersNonMatchingClauses,
  FiltersMatchingClauses
}
import persistence.filters.scoring.SimilarityQueryBuilder

object FiltersConverter {
  private def buildBaseCypher(
      matchingFilters: Seq[Option[CypherFragment]],
      nonMatchingFilters: Seq[Option[CypherFragment]],
      nodeVar: String
  ): CypherFragment = {
    val matching = matchingFilters.flatten.filter(nonEmpty)
    val nonMatching = nonMatchingFilters.flatten.filter(nonEmpty)

    val matchingCypher = matching.map(_.cypher).mkString(" \n ")
    val nonMatchingCypher = nonMatching.map(_.cypher).mkString(" AND ")
    val wherePrefix =
      if (nonMatching.nonEmpty) s"MATCH ($nodeVar) WHERE  " else ""

    CypherFragment(
      matchingCypher + wherePrefix + nonMatchingCypher,
      mergeParams(matching ++ nonMatching)
    )
  }

  def toCypher(filters: Filters, nodeVar: String): CypherFragment = {
    val matchingFilters =
      FiltersMatchingClauses.matchingFilters(filters, nodeVar)
    val nonMatchingFilters =
      FiltersNonMatchingClauses.nonMatchingFilters(filters, nodeVar)

    val base = buildBaseCypher(matchingFilters, nonMatchingFilters, nodeVar)
    SimilarityQueryBuilder(base, filters, nodeVar)
  }
}
