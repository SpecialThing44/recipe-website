package persistence.filters.scoring
import CypherScoringSupport.{
  appendMinWhere,
  jaccardScoreTail
}

object CoSaveScoringParts {
  import CypherScoringSupport.{appendMinWhere, jaccardScoreTail}

  def recipeRecipeCoSavePart(
      nodeVar: String,
      coSaveCarry: String,
      coSaveMinParam: Option[String]
  ): String =
    s"""
       |WITH $coSaveCarry
       |OPTIONAL MATCH (target)-[:SAVED_BY]->(ur:User)
       |WITH $coSaveCarry, collect(DISTINCT ur.id) AS usersT
       |OPTIONAL MATCH ($nodeVar)-[:SAVED_BY]->(uc:User)
       |WITH $coSaveCarry, usersT, collect(DISTINCT uc.id) AS usersC
       |""".stripMargin + jaccardScoreTail(
      coSaveCarry,
      "usersT",
      "usersC",
      "inter",
      "sizeT",
      "sizeC",
      "coSaveScore"
    ) + appendMinWhere("coSaveScore", coSaveMinParam)

  def userUserCoSavePart(
      nodeVar: String,
      coSaveCarry: String,
      coSaveMinParam: Option[String]
  ): String =
    s"""
       |WITH $coSaveCarry
       |OPTIONAL MATCH (target)<-[:SAVED_BY]-(tr:Recipe)
       |OPTIONAL MATCH (target)<-[:CREATED_BY]-(tcr:Recipe)
       |WITH $coSaveCarry, (collect(DISTINCT tr.id) + collect(DISTINCT tcr.id)) AS recipesT
       |OPTIONAL MATCH ($nodeVar)<-[:SAVED_BY]-(cr:Recipe)
       |OPTIONAL MATCH ($nodeVar)<-[:CREATED_BY]-(ccr:Recipe)
       |WITH $coSaveCarry, recipesT, (collect(DISTINCT cr.id) + collect(DISTINCT ccr.id)) AS recipesC
       |""".stripMargin + jaccardScoreTail(
      coSaveCarry,
      "recipesT",
      "recipesC",
      "inter",
      "sizeT",
      "sizeC",
      "coSaveScore"
    ) + appendMinWhere("coSaveScore", coSaveMinParam)
}
