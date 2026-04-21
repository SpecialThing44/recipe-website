package persistence.ingredients.weights

import com.google.inject.{Inject, Singleton}
import persistence.neo4j.Database
import persistence.recipes.RecipePersistence
import zio.{Task, ZIO}

import scala.jdk.CollectionConverters.*

@Singleton
class RebuildAllIngredientsJob @Inject() (
    database: Database,
  recipePersistence: RecipePersistence
) {

  def run(meanRawPenaltyFactor: Double): Task[(Int, String)] =
    for {
      totalRecipes <- recipePersistence.getTotalRecipeCount()
      ingredientIds <- getAllIngredientIds()
      _ <- recomputeIngredientStats(
        ingredientIds,
        meanRawPenaltyFactor,
        totalRecipes
      )
      processed = ingredientIds.distinct.size
    } yield (processed, s"{\"processedIngredients\":$processed}")

  private def getAllIngredientIds(): Task[Seq[String]] =
    database.readTransaction(
      """
        |MATCH (ingredient:Ingredient)
        |RETURN ingredient.id AS ingredientId
        |""".stripMargin,
      (result: org.neo4j.driver.Result) =>
        result.asScala.map(record => record.get("ingredientId").asString()).toSeq
    )

  private def recomputeIngredientStats(
      ingredientIds: Seq[String],
      meanRawPenaltyFactor: Double,
      totalRecipes: Int
  ): Task[Unit] =
    if (ingredientIds.isEmpty) ZIO.unit
    else {
      database.writeTransaction(
        s"""
           |UNWIND $$ingredientIds AS ingredientId
           |MATCH (ingredient:Ingredient {id: ingredientId})
           |OPTIONAL MATCH (recipe:Recipe)-[ri:HAS_INGREDIENT]->(ingredient)
           |WITH ingredient, $$totalRecipes AS totalRecipes,
           |     count(DISTINCT recipe) AS recipeCount,
           |     coalesce(sum(coalesce(ri.rawNormalizedWeight, ri.normalizedWeight, 0.0)), 0.0) AS sumRawNormalizedWeight
           |${IngredientWeightJobSupport.applyWeightStatsCypher}
           |""".stripMargin,
        Map(
          "ingredientIds" -> ingredientIds.distinct.asJava,
          "meanRawPenaltyFactor" -> Double.box(meanRawPenaltyFactor),
          "totalRecipes" -> Int.box(totalRecipes)
        ),
        (_: org.neo4j.driver.Result) => ()
      )
    }
}