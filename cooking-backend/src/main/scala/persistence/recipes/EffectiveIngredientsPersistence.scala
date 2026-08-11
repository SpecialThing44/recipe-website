package persistence.recipes

import com.google.inject.{Inject, Singleton}
import persistence.neo4j.Database
import zio.Task

import java.util.UUID
import scala.jdk.CollectionConverters.*

@Singleton
class EffectiveIngredientsPersistence @Inject() (database: Database) {
  def refreshAffectedBy(recipeIds: Seq[UUID]): Task[Unit] =
    if (recipeIds.isEmpty) zio.ZIO.unit
    else
      database.writeTransaction(
        s"""
           |MATCH (changed:Recipe)
           |WHERE changed.id IN $$recipeIds
           |MATCH (affected:Recipe)-[:HAS_RECIPE*0..]->(changed)
           |WITH collect(DISTINCT affected) AS affectedRecipes
           |UNWIND affectedRecipes AS affected
           |OPTIONAL MATCH (affected)-[old:HAS_EFFECTIVE_INGREDIENT]->()
           |DELETE old
           |WITH DISTINCT affected
           |MATCH path = (affected)-[:HAS_RECIPE*0..]->(sourceRecipe)
           |MATCH (sourceRecipe)-[direct:HAS_INGREDIENT]->(ingredient:Ingredient)
           |WITH affected, ingredient,
           |     sum(direct.weight * reduce(
           |       multiplier = 1.0,
           |       component IN relationships(path) |
           |       multiplier * CASE
           |         WHEN component.unit = 'serving' THEN
           |           toFloat(component.amount) / toFloat(coalesce(endNode(component).servings, 1))
           |         ELSE 1.0
           |       END
           |     )) AS effectiveWeight
           |WITH affected,
           |     collect({ingredient: ingredient, weight: effectiveWeight}) AS ingredientWeights,
           |     sum(effectiveWeight) AS totalWeight
           |UNWIND ingredientWeights AS ingredientWeight
           |WITH affected, ingredientWeight, totalWeight,
           |     ingredientWeight.ingredient AS ingredient
           |CREATE (affected)-[:HAS_EFFECTIVE_INGREDIENT {
           |  weight: ingredientWeight.weight,
           |  rawNormalizedWeight: CASE
           |    WHEN totalWeight = 0 THEN 0.0
           |    ELSE ingredientWeight.weight / totalWeight
           |  END
           |}]->(ingredient)
           |RETURN count(*) AS updatedCount
           |""".stripMargin,
        Map("recipeIds" -> recipeIds.map(_.toString).asJava),
        (_: org.neo4j.driver.Result) => ()
      )

  def refreshAll(): Task[Unit] =
    database
      .readTransaction(
        "MATCH (recipe:Recipe) RETURN recipe.id AS id",
        (result: org.neo4j.driver.Result) =>
          result.asScala
            .map(record => UUID.fromString(record.get("id").asString()))
            .toSeq
      )
      .flatMap(refreshAffectedBy)

  def ancestorIdsOf(recipeId: UUID): Task[Seq[UUID]] =
    database.readTransaction(
      s"""
         |MATCH (ancestor:Recipe)-[:HAS_RECIPE*1..]->(:Recipe {id: $$recipeId})
         |RETURN DISTINCT ancestor.id AS id
         |""".stripMargin,
      Map("recipeId" -> recipeId.toString),
      (result: org.neo4j.driver.Result) =>
        result.asScala
          .map(record => UUID.fromString(record.get("id").asString()))
          .toSeq
    )
}
