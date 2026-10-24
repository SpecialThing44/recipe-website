package persistence.filters

import domain.filters.Filters
import persistence.filters.scoring.SimilarityQueryBuilder

final case class CypherFragment(cypher: String, params: Map[String, AnyRef])

object CypherFragment {
  val empty: CypherFragment = CypherFragment("", Map.empty)

  def getOrderLine(filters: Filters, nodeVar: String): String = {
    if (SimilarityQueryBuilder.isActive(filters, nodeVar)) {
      "ORDER BY score DESC"
    } else if (
      filters.orderBy.isDefined && filters.orderBy.get.name.isDefined
    ) {
      s"ORDER BY $nodeVar.name"
    } else {
      ""
    }
  }

  def getWithScoreLine(filters: Filters, withStatement: String, nodeVar: String): String =
    if (SimilarityQueryBuilder.isActive(filters, nodeVar)) withStatement + ", score"
    else withStatement

  def limitAndSkipStatement(filters: Filters): CypherFragment =
    filters.limit
      .map(limitValue => {
        val pageValue = filters.page.getOrElse(0)
        val limitParam = "filter_limit"
        val skipParam = "filter_skip"
        CypherFragment(
          s"SKIP $$${skipParam} LIMIT $$${limitParam}",
          Map(
            skipParam -> Int.box(pageValue * limitValue),
            limitParam -> Int.box(limitValue)
          )
        )
      })
      .getOrElse(CypherFragment.empty)
}

trait Cypher[A] {
  def toCypher(
      filter: A,
      property: String,
      nodeVar: String,
      paramPrefix: String
  ): CypherFragment
}
