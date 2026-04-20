package persistence.filters.scoring
import CypherScoringSupport.{
  appendMinWhere,
  jaccardScoreTail
}

object TagScoringParts {
  import CypherScoringSupport.{appendMinWhere, jaccardScoreTail}

  def recipeRecipeTagPart(
      nodeVar: String,
      tagCarryBase: String,
      tagMinParam: Option[String]
  ): String =
    s"""
       |WITH $tagCarryBase
       |OPTIONAL MATCH (target)-[:HAS_TAG]->(tt:Tag)
       |WITH $tagCarryBase, collect(DISTINCT tt.name) AS targetTags
       |OPTIONAL MATCH ($nodeVar)-[:HAS_TAG]->(ct:Tag)
       |WITH $tagCarryBase, targetTags, collect(DISTINCT ct.name) AS candidateTags
       |""".stripMargin + jaccardScoreTail(
      tagCarryBase,
      "targetTags",
      "candidateTags",
      "interTags",
      "sizeTargetTags",
      "sizeCandidateTags",
      "tagScore"
    ) + appendMinWhere("tagScore", tagMinParam)

  def userRecipeTagPart(
      nodeVar: String,
      tagCarryBase: String,
      tagMinParam: Option[String]
  ): String =
    s"""
       |WITH $tagCarryBase
       |OPTIONAL MATCH (target)<-[:SAVED_BY]-(tr:Recipe)
       |OPTIONAL MATCH (target)<-[:CREATED_BY]-(tcr:Recipe)
       |WITH $tagCarryBase, (collect(DISTINCT tr) + collect(DISTINCT tcr)) AS recipesT
       |WHERE NOT $nodeVar IN recipesT
       |UNWIND recipesT AS tRec
       |MATCH (tRec)-[:HAS_TAG]->(tt:Tag)
       |WITH $tagCarryBase, collect(DISTINCT tt.name) AS targetTags
       |OPTIONAL MATCH ($nodeVar)-[:HAS_TAG]->(ct:Tag)
       |WITH $tagCarryBase, targetTags, collect(DISTINCT ct.name) AS candidateTags
       |""".stripMargin + jaccardScoreTail(
      tagCarryBase,
      "targetTags",
      "candidateTags",
      "interTags",
      "sizeTargetTags",
      "sizeCandidateTags",
      "tagScore"
    ) + appendMinWhere("tagScore", tagMinParam)

  def userUserTagPart(
      nodeVar: String,
      tagCarryBase: String,
      tagMinParam: Option[String]
  ): String =
    s"""
       |WITH $tagCarryBase
       |OPTIONAL MATCH (tss:Recipe)-[:SAVED_BY]->(target)
       |OPTIONAL MATCH (tcc:Recipe)-[:CREATED_BY]->(target)
       |OPTIONAL MATCH (css:Recipe)-[:SAVED_BY]->($nodeVar)
       |OPTIONAL MATCH (ccc:Recipe)-[:CREATED_BY]->($nodeVar)
       |WITH $tagCarryBase,
       |     collect(DISTINCT tss) + collect(DISTINCT tcc) AS tAllTags,
       |     collect(DISTINCT css) + collect(DISTINCT ccc) AS cAllTags
       |WITH $tagCarryBase, tAllTags, cAllTags,
       |     CASE WHEN size(tAllTags) = 0 THEN [null] ELSE tAllTags END AS tAllTagsNZ,
       |     CASE WHEN size(cAllTags) = 0 THEN [null] ELSE cAllTags END AS cAllTagsNZ
       |UNWIND tAllTagsNZ AS tTagRec
       |OPTIONAL MATCH (tTagRec)-[:HAS_TAG]->(tt:Tag)
       |WITH $tagCarryBase, cAllTagsNZ, collect(DISTINCT tt.name) AS targetTags
       |UNWIND cAllTagsNZ AS cTagRec
       |OPTIONAL MATCH (cTagRec)-[:HAS_TAG]->(ct:Tag)
       |WITH $tagCarryBase, targetTags, collect(DISTINCT ct.name) AS candidateTags
       |""".stripMargin + jaccardScoreTail(
      tagCarryBase,
      "targetTags",
      "candidateTags",
      "interTags",
      "sizeTargetTags",
      "sizeCandidateTags",
      "tagScore"
    ) + appendMinWhere("tagScore", tagMinParam)
}
