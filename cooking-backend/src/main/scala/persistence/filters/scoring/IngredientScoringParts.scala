package persistence.filters.scoring
import CypherScoringSupport.{
  appendMinWhere,
  ingredientCosineScoreTail
}

object IngredientScoringParts {
  import CypherScoringSupport.{appendMinWhere, ingredientCosineScoreTail}

  def recipeRecipeIngredientPart(
      nodeVar: String,
      ingredientMinParam: Option[String]
  ): String =
    s"""
       |MATCH (target)-[tr:HAS_INGREDIENT]->(ti:Ingredient)
       |WITH $nodeVar, target, collect({ingredientId: ti.id, weight: coalesce(tr.rawNormalizedWeight, tr.normalizedWeight, 0.0) * coalesce(ti.globalWeight, 1.0)}) AS targetVector
       |MATCH ($nodeVar)-[ci:HAS_INGREDIENT]->(ciIngr:Ingredient)
       |WITH $nodeVar, target, targetVector, collect({ingredientId: ciIngr.id, weight: coalesce(ci.rawNormalizedWeight, ci.normalizedWeight, 0.0) * coalesce(ciIngr.globalWeight, 1.0)}) AS candidateVector
       |""".stripMargin + ingredientCosineScoreTail(nodeVar) + appendMinWhere(
      "ingredientScore",
      ingredientMinParam
    )

  def userRecipeIngredientPart(
      nodeVar: String,
      ingredientMinParam: Option[String]
  ): String =
    s"""
       |OPTIONAL MATCH (target)<-[:SAVED_BY]-(tr:Recipe)
       |OPTIONAL MATCH (target)<-[:CREATED_BY]-(tcr:Recipe)
       |WITH $nodeVar, target, (collect(DISTINCT tr) + collect(DISTINCT tcr)) AS recipesT
       |WHERE NOT $nodeVar IN recipesT
       |UNWIND recipesT AS tRec
       |MATCH (tRec)-[tr:HAS_INGREDIENT]->(ti:Ingredient)
       |WITH $nodeVar, target, ti.id AS ingrIdT, sum(coalesce(tr.rawNormalizedWeight, tr.normalizedWeight, 0.0) * coalesce(ti.globalWeight, 1.0)) AS weightT
       |WITH $nodeVar, target, collect({ingredientId: ingrIdT, weight: weightT}) AS targetVector
       |MATCH ($nodeVar)-[ci:HAS_INGREDIENT]->(ciIngr:Ingredient)
       |WITH $nodeVar, target, targetVector, collect({ingredientId: ciIngr.id, weight: coalesce(ci.rawNormalizedWeight, ci.normalizedWeight, 0.0) * coalesce(ciIngr.globalWeight, 1.0)}) AS candidateVector
       |""".stripMargin + ingredientCosineScoreTail(nodeVar) + appendMinWhere(
      "ingredientScore",
      ingredientMinParam
    )

  def userUserIngredientPart(
      nodeVar: String,
      ingredientMinParam: Option[String]
  ): String =
    s"""
       |OPTIONAL MATCH (ts:Recipe)-[:SAVED_BY]->(target)
       |OPTIONAL MATCH (tc:Recipe)-[:CREATED_BY]->(target)
       |OPTIONAL MATCH (cs:Recipe)-[:SAVED_BY]->($nodeVar)
       |OPTIONAL MATCH (cc:Recipe)-[:CREATED_BY]->($nodeVar)
       |WITH $nodeVar, target,
       |     collect(DISTINCT ts) + collect(DISTINCT tc) AS tAll,
       |     collect(DISTINCT cs) + collect(DISTINCT cc) AS cAll
       |WITH $nodeVar, target, tAll, cAll,
       |     CASE WHEN size(tAll) = 0 THEN [null] ELSE tAll END AS tAllNZ,
       |     CASE WHEN size(cAll) = 0 THEN [null] ELSE cAll END AS cAllNZ
       |UNWIND tAllNZ AS tRec
       |OPTIONAL MATCH (tRec)-[tr:HAS_INGREDIENT]->(ti:Ingredient)
       |WITH $nodeVar, target, ti.id AS ingrIdT, sum(coalesce(tr.rawNormalizedWeight, tr.normalizedWeight, 0.0) * coalesce(ti.globalWeight, 1.0)) AS weightT, cAllNZ
       |WITH $nodeVar, target, collect({ingredientId: ingrIdT, weight: weightT}) AS targetVector, cAllNZ
       |UNWIND cAllNZ AS cRec
       |OPTIONAL MATCH (cRec)-[ci:HAS_INGREDIENT]->(ciIngr:Ingredient)
       |WITH $nodeVar, target, targetVector, ciIngr.id AS ingrIdC, sum(coalesce(ci.rawNormalizedWeight, ci.normalizedWeight, 0.0) * coalesce(ciIngr.globalWeight, 1.0)) AS weightC
       |WITH $nodeVar, target, targetVector, collect({ingredientId: ingrIdC, weight: weightC}) AS candidateVector
       |""".stripMargin + ingredientCosineScoreTail(nodeVar) + appendMinWhere(
      "ingredientScore",
      ingredientMinParam
    )
}
