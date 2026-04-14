package persistence.filters

object CypherScoringParts {
  private def appendMinWhere(scoreName: String, min: Option[Double]): String =
    min.map(minValue => s"\nWHERE $scoreName >= $minValue ").getOrElse("")

  private def ingredientCosineScoreTail(nodeVar: String): String =
    s"""
       |WITH $nodeVar, target, targetVector, candidateVector,
       |     reduce(dot = 0.0, x IN targetVector | dot + coalesce([y IN candidateVector WHERE y.ingredientId = x.ingredientId][0].weight, 0.0) * x.weight) AS dotProd,
       |     sqrt(reduce(s = 0.0, x IN targetVector | s + x.weight * x.weight)) AS normTarget,
       |     sqrt(reduce(s = 0.0, x IN candidateVector | s + x.weight * x.weight)) AS normCandidate
       |WITH $nodeVar, target, CASE WHEN normTarget = 0 OR normCandidate = 0 THEN 0.0 ELSE dotProd / (normTarget * normCandidate) END AS ingredientScore
       |""".stripMargin

  private def jaccardScoreTail(
      carry: String,
      leftCollection: String,
      rightCollection: String,
      interName: String,
      sizeLeftName: String,
      sizeRightName: String,
      scoreName: String
  ): String =
    s"""
       |WITH $carry,
       |     size([x IN $rightCollection WHERE x IN $leftCollection]) AS $interName,
       |     size($leftCollection) AS $sizeLeftName,
       |     size($rightCollection) AS $sizeRightName
       |WITH $carry, $interName, $sizeLeftName, $sizeRightName,
       |     CASE WHEN $sizeLeftName + $sizeRightName - $interName = 0 THEN 0.0 ELSE toFloat($interName) / toFloat($sizeLeftName + $sizeRightName - $interName) END AS $scoreName
       |""".stripMargin

  def recipeRecipeIngredientPart(
      nodeVar: String,
      ingredientMin: Option[Double]
  ): String =
    s"""
       |MATCH (target)-[tr:HAS_INGREDIENT]->(ti:Ingredient)
       |WITH $nodeVar, target, collect({ingredientId: ti.id, weight: coalesce(tr.normalizedWeight, 0.0)}) AS targetVector
       |MATCH ($nodeVar)-[ci:HAS_INGREDIENT]->(ciIngr:Ingredient)
       |WITH $nodeVar, target, targetVector, collect({ingredientId: ciIngr.id, weight: coalesce(ci.normalizedWeight, 0.0)}) AS candidateVector
       |""".stripMargin + ingredientCosineScoreTail(nodeVar) + appendMinWhere(
      "ingredientScore",
      ingredientMin
    )

  def userRecipeIngredientPart(
      nodeVar: String,
      ingredientMin: Option[Double]
  ): String =
    s"""
       |OPTIONAL MATCH (target)<-[:SAVED_BY]-(tr:Recipe)
       |OPTIONAL MATCH (target)<-[:CREATED_BY]-(tcr:Recipe)
       |WITH $nodeVar, target, (collect(DISTINCT tr) + collect(DISTINCT tcr)) AS recipesT
       |WHERE NOT $nodeVar IN recipesT
       |UNWIND recipesT AS tRec
       |MATCH (tRec)-[tr:HAS_INGREDIENT]->(ti:Ingredient)
       |WITH $nodeVar, target, ti.id AS ingrIdT, sum(coalesce(tr.normalizedWeight, 0.0)) AS weightT
       |WITH $nodeVar, target, collect({ingredientId: ingrIdT, weight: weightT}) AS targetVector
       |MATCH ($nodeVar)-[ci:HAS_INGREDIENT]->(ciIngr:Ingredient)
       |WITH $nodeVar, target, targetVector, collect({ingredientId: ciIngr.id, weight: coalesce(ci.normalizedWeight, 0.0)}) AS candidateVector
       |""".stripMargin + ingredientCosineScoreTail(nodeVar) + appendMinWhere(
      "ingredientScore",
      ingredientMin
    )

  def userUserIngredientPart(
      nodeVar: String,
      ingredientMin: Option[Double]
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
       |WITH $nodeVar, target, ti.id AS ingrIdT, sum(coalesce(tr.normalizedWeight, 0.0)) AS weightT, cAllNZ
       |WITH $nodeVar, target, collect({ingredientId: ingrIdT, weight: weightT}) AS targetVector, cAllNZ
       |UNWIND cAllNZ AS cRec
       |OPTIONAL MATCH (cRec)-[ci:HAS_INGREDIENT]->(ciIngr:Ingredient)
       |WITH $nodeVar, target, targetVector, ciIngr.id AS ingrIdC, sum(coalesce(ci.normalizedWeight, 0.0)) AS weightC
       |WITH $nodeVar, target, targetVector, collect({ingredientId: ingrIdC, weight: weightC}) AS candidateVector
       |""".stripMargin + ingredientCosineScoreTail(nodeVar) + appendMinWhere(
      "ingredientScore",
      ingredientMin
    )

  def recipeRecipeCoSavePart(
      nodeVar: String,
      coSaveCarry: String,
      coSaveMin: Option[Double]
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
    ) + appendMinWhere("coSaveScore", coSaveMin)

  def userUserCoSavePart(
      nodeVar: String,
      coSaveCarry: String,
      coSaveMin: Option[Double]
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
    ) + appendMinWhere("coSaveScore", coSaveMin)

  def recipeRecipeTagPart(
      nodeVar: String,
      tagCarryBase: String,
      tagMin: Option[Double]
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
    ) + appendMinWhere("tagScore", tagMin)

  def userRecipeTagPart(
      nodeVar: String,
      tagCarryBase: String,
      tagMin: Option[Double]
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
    ) + appendMinWhere("tagScore", tagMin)

  def userUserTagPart(
      nodeVar: String,
      tagCarryBase: String,
      tagMin: Option[Double]
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
    ) + appendMinWhere("tagScore", tagMin)
}