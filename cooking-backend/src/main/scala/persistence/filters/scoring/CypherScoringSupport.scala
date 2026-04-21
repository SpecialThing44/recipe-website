package persistence.filters.scoring

object CypherScoringSupport {
  def appendMinWhere(scoreName: String, minParam: Option[String]): String =
    minParam.map(param => s"\nWHERE $scoreName >= $$${param} ").getOrElse("")

  def ingredientCosineScoreTail(nodeVar: String): String =
    s"""
       |WITH $nodeVar, target, targetVector, candidateVector,
       |     reduce(dot = 0.0, x IN targetVector | dot + coalesce([y IN candidateVector WHERE y.ingredientId = x.ingredientId][0].weight, 0.0) * x.weight) AS dotProd,
       |     sqrt(reduce(s = 0.0, x IN targetVector | s + x.weight * x.weight)) AS normTarget,
       |     sqrt(reduce(s = 0.0, x IN candidateVector | s + x.weight * x.weight)) AS normCandidate
       |WITH $nodeVar, target, CASE WHEN normTarget = 0 OR normCandidate = 0 THEN 0.0 ELSE dotProd / (normTarget * normCandidate) END AS ingredientScore
       |""".stripMargin

  def jaccardScoreTail(
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
}
