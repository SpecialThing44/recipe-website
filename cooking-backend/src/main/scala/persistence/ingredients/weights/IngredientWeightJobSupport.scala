package persistence.ingredients.weights

object IngredientWeightJobSupport {
  //   meanRawNormalizedWeight =
  //     if recipeCount == 0 then 0.0 else sumRawNormalizedWeight / recipeCount
  //
  //   inverseDocumentFrequency = log((totalRecipes + 1.0) / (recipeCount + 1.0)) + 1.0
  //
  //   quantityPenalty = 1.0 / (1.0 + (meanRawPenaltyFactor * meanRawNormalizedWeight))
  //
  //   globalWeight =
  //     if totalRecipes == 0 or recipeCount == 0 then 1.0
  //     else inverseDocumentFrequency * quantityPenalty
  //
  val applyWeightStatsCypher: String =
    """
      |WITH ingredient, totalRecipes, recipeCount, sumRawNormalizedWeight,
      |     CASE WHEN recipeCount = 0 THEN 0.0 ELSE sumRawNormalizedWeight / toFloat(recipeCount) END AS meanRawNormalizedWeight
      |WITH ingredient, totalRecipes, recipeCount, sumRawNormalizedWeight, meanRawNormalizedWeight,
      |     (log((toFloat(totalRecipes) + 1.0) / (toFloat(recipeCount) + 1.0)) + 1.0) AS inverseDocumentFrequency,
      |     (1.0 / (1.0 + ($$meanRawPenaltyFactor * meanRawNormalizedWeight))) AS quantityPenalty
      |SET ingredient.recipeCount = recipeCount,
      |    ingredient.sumRawNormalizedWeight = sumRawNormalizedWeight,
      |    ingredient.meanRawNormalizedWeight = meanRawNormalizedWeight,
      |    ingredient.globalWeight = CASE
      |      WHEN totalRecipes = 0 OR recipeCount = 0 THEN 1.0
      |      ELSE inverseDocumentFrequency * quantityPenalty
      |    END,
      |    ingredient.weightUpdatedAt = datetime(),
      |    ingredient.weightVersion = coalesce(ingredient.weightVersion, 0) + 1
      |RETURN count(ingredient) AS updatedCount
      |""".stripMargin
}