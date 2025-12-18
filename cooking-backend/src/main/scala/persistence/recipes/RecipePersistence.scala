package persistence.recipes

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.ingredients.Unit
import domain.recipes.Recipe
import persistence.cypher.*
import persistence.filters.FiltersConverter
import persistence.neo4j.Database
import zio.ZIO

import java.util.UUID
import scala.jdk.CollectionConverters.*

class RecipePersistence @Inject() (database: Database) extends Recipes {
  private implicit val graph: RecipeGraph = RecipeGraph()

  private def createIngredientStatementsFor(
      entity: Recipe,
      useIngredientAliasSuffix: Boolean
  ): String = {
    val standardizedWeights = entity.ingredients.map(ii =>
      Unit.toStandardizedAmount(ii.quantity.unit, ii.quantity.amount)
    )
    val totalStandardizedExcludingWater = entity.ingredients
      .zip(standardizedWeights)
      .filter { case (ingredientInput, _) =>
        !ingredientInput.ingredient.name.equalsIgnoreCase("Water")
      }
      .map(_._2.toDouble)
      .sum
    entity.ingredients
      .zip(standardizedWeights)
      .map { case (ingredientInput, standardizedWeight) =>
        val normalizedWeight =
          if (ingredientInput.ingredient.name.equalsIgnoreCase("Water")) 0.0
          else if (totalStandardizedExcludingWater == 0) 0.0
          else standardizedWeight.toDouble / totalStandardizedExcludingWater
        val aliasSuffix =
          if (useIngredientAliasSuffix)
            ingredientInput.ingredient.id.toString.replace("-", "")
          else ""
        val ingredientMatch =
          if (useIngredientAliasSuffix) s"ing$aliasSuffix" else "ing"
        s"""
         |MATCH ($ingredientMatch:Ingredient {id: '%s'})
         |CREATE (${graph.nodeVar})-[:HAS_INGREDIENT {amount: %f, unit: '%s', weight: %f, normalizedWeight: %f, description: '%s'}]->($ingredientMatch)
         |${WithStatement.apply}, user
         |""".stripMargin.format(
          ingredientInput.ingredient.id.toString,
          Double.box(ingredientInput.quantity.amount),
          ingredientInput.quantity.unit.name,
          Double.box(standardizedWeight),
          Double.box(normalizedWeight),
          ingredientInput.description.getOrElse("")
        )
      }
      .mkString("\n")
  }

  override def list(query: Filters): ZIO[ApiContext, Throwable, Seq[Recipe]] = {
    val includeIngredientScore =
      query.ingredientSimilarity.isDefined && query.analyzedEntity.isDefined
    val withLine =
      s"WITH ${graph.nodeVar}, user, collect(DISTINCT ${graph.tagVar}.name) as tags, collect(DISTINCT {ingredient: properties(ingredient), amount: ri.amount, unit: ri.unit, weight: ri.weight, description: ri.description}) as ingredientQuantities"
    val orderLine = FiltersConverter.getOrderLine(query, graph.nodeVar)
    database.readTransaction(
      s"""
         |${MatchStatement.apply}
         |${FiltersConverter.toCypher(query, graph.nodeVar)}
         |${MatchRelationship.outgoing("CREATED_BY", "user", "User")}
         |OPTIONAL MATCH (${graph.nodeVar})-[ri:HAS_INGREDIENT]->(ingredient:Ingredient)
         |OPTIONAL ${MatchRelationship.outgoing(
          graph.tagRelation,
          "tag",
          graph.tagLabel
        )}
         |${FiltersConverter.getWithScoreLine(query, withLine)}
         |$orderLine
         |${query.limitAndSkipStatement}
         |${ReturnStatement.apply}, user as createdBy, tags, ingredientQuantities
         |""".stripMargin,
      (result: org.neo4j.driver.Result) =>
        result.asScala
          .map(record => attachAllToRecord(record))
          .toSeq
    )
  }

  override def create(entity: Recipe): ZIO[ApiContext, Throwable, Recipe] = {
    val properties = RecipeConverter.convert(entity)
    val createTagStatements = graph.createTagStatementsFor(
      graph.nodeVar,
      graph.tagRelation,
      graph.tagLabel,
      entity.tags,
      includeWithUser = true,
      useAliasSuffix = false
    )
    val createIngredientStatements =
      createIngredientStatementsFor(entity, useIngredientAliasSuffix = false)

    database.writeTransaction(
      s"""
         |CREATE (${graph.nodeVar}:${graph.nodeLabel} {
         |$properties
         |})
         |${WithStatement.apply}
         |MATCH (user:User {id: '${entity.createdBy.id}'})
         |CREATE (${graph.nodeVar})-[:CREATED_BY]->(user)
         |${WithStatement.apply}, user
         |$createTagStatements
         |$createIngredientStatements
         |OPTIONAL MATCH (${graph.nodeVar})-[ri:HAS_INGREDIENT]->(ingredient:Ingredient)
         |OPTIONAL ${MatchRelationship.outgoing(
          graph.tagRelation,
          "tag",
          graph.tagLabel
        )}
         |WITH ${graph.nodeVar}, user, collect(DISTINCT ${graph.tagVar}.name) as tags, collect(DISTINCT {ingredient: properties(ingredient), amount: ri.amount, unit: ri.unit, weight: ri.weight, normalizedWeight: coalesce(ri.normalizedWeight, 0.0), description: ri.description}) as ingredientQuantities
         |${ReturnStatement.apply}, user as createdBy, tags, ingredientQuantities
         |""".stripMargin,
      (result: org.neo4j.driver.Result) => {
        if (result.hasNext) {
          attachAllToRecord(result.next())
        } else {
          throw domain.types.NoSuchEntityError(
            s"Create for ${graph.nodeLabel} has failed for some reason"
          )
        }
      }
    )
  }

  override def update(
      entity: Recipe,
      originalEntity: Recipe
  ): ZIO[ApiContext, Throwable, Recipe] = {
    val properties = RecipeConverter.convertForUpdate(graph.nodeVar, entity)
    val createTagStatements = graph.createTagStatementsFor(
      graph.nodeVar,
      graph.tagRelation,
      graph.tagLabel,
      entity.tags,
      includeWithUser = true,
      useAliasSuffix = true
    )
    val createIngredientStatements =
      createIngredientStatementsFor(entity, useIngredientAliasSuffix = true)

    database.writeTransaction(
      s"""
         |${MatchByIdStatement.apply(entity.id)}
         |SET $properties
         |${WithStatement.apply}
         |OPTIONAL MATCH (${graph.nodeVar})-[r:${graph.tagRelation}]->()
         |DELETE r
         |${WithStatement.apply}
         |OPTIONAL MATCH (${graph.nodeVar})-[ri:HAS_INGREDIENT]->()
         |DELETE ri
         |${WithStatement.apply}
         |${MatchRelationship.outgoing("CREATED_BY", "user", "User")}
         |${WithStatement.apply}, user
         |$createTagStatements
         |$createIngredientStatements
         |${WithStatement.apply}, user
         |OPTIONAL MATCH (${graph.nodeVar})-[ri:HAS_INGREDIENT]->(ingredient:Ingredient)
         |OPTIONAL ${MatchRelationship.outgoing(
          graph.tagRelation,
          graph.tagVar,
          graph.tagLabel
        )}
         |WITH ${graph.nodeVar}, user, collect(DISTINCT ${graph.tagVar}.name) as tags, collect(DISTINCT {ingredient: properties(ingredient), amount: ri.amount, unit: ri.unit, weight: ri.weight, normalizedWeight: coalesce(ri.normalizedWeight, 0.0), description: ri.description}) as ingredientQuantities
         |${ReturnStatement.apply}, user as createdBy, tags, ingredientQuantities
         |""".stripMargin,
      (result: org.neo4j.driver.Result) => {
        if (result.hasNext) {
          attachAllToRecord(result.next())
        } else {
          throw domain.types.NoSuchEntityError(
            s"Update for ${graph.nodeLabel} with id ${entity.id} has failed for some reason"
          )
        }
      }
    )
  }

  override def delete(id: UUID): ZIO[ApiContext, Throwable, Recipe] =
    for {
      recipe <- getById(id)
      _ <- database.writeTransaction(
        s"""
           |${MatchByIdStatement.apply(id)}
           |${DeleteStatement.apply}
           |""".stripMargin,
        (_: org.neo4j.driver.Result) => ()
      )
    } yield recipe

  override def getById(id: UUID): ZIO[ApiContext, Throwable, Recipe] =
    database.readTransaction(
      s"""
         |${MatchByIdStatement.apply(id)}
         |${MatchRelationship.outgoing("CREATED_BY", "user", "User")}
         |OPTIONAL MATCH (${graph.nodeVar})-[ri:HAS_INGREDIENT]->(ingredient:Ingredient)
         |OPTIONAL ${MatchRelationship.outgoing(
          graph.tagRelation,
          graph.tagVar,
          graph.tagLabel
        )}
         |WITH ${graph.nodeVar}, user, collect(DISTINCT ${graph.tagVar}.name) as tags, collect(DISTINCT {ingredient: properties(ingredient), amount: ri.amount, unit: ri.unit, weight: ri.weight, normalizedWeight: coalesce(ri.normalizedWeight, 0.0), description: ri.description}) as ingredientQuantities
         |${ReturnStatement.apply}, user as createdBy, tags, ingredientQuantities
         |""".stripMargin,
      (result: org.neo4j.driver.Result) => {
        if (result.hasNext) {
          attachAllToRecord(result.next())
        } else {
          throw domain.types.NoSuchEntityError(
            s"${graph.nodeLabel} with id $id not found"
          )
        }
      }
    )

  override def save(
      recipeId: UUID,
      userId: UUID
  ): ZIO[ApiContext, Throwable, Recipe] =
    database.writeTransaction(
      s"""
         |MATCH (${graph.nodeVar}:${graph.nodeLabel} {id: '$recipeId'})
         |MATCH (user:User {id: '$userId'})
         |MERGE (${graph.nodeVar})-[:SAVED_BY]->(user)
         |${WithStatement.apply}
         |${MatchRelationship.outgoing("CREATED_BY", "created", "User")}
         |WITH ${graph.nodeVar}, created as user
         |OPTIONAL MATCH (${graph.nodeVar})-[ri:HAS_INGREDIENT]->(ingredient:Ingredient)
         |OPTIONAL ${MatchRelationship.outgoing(
          graph.tagRelation,
          graph.tagVar,
          graph.tagLabel
        )}
         |WITH ${graph.nodeVar}, user, collect(DISTINCT ${graph.tagVar}.name) as tags, collect(DISTINCT {ingredient: properties(ingredient), amount: ri.amount, unit: ri.unit, weight: ri.weight, normalizedWeight: coalesce(ri.normalizedWeight, 0.0), description: ri.description}) as ingredientQuantities
         |${ReturnStatement.apply}, user as createdBy, tags, ingredientQuantities
         |""".stripMargin,
      (result: org.neo4j.driver.Result) => {
        if (result.hasNext) attachAllToRecord(result.next())
        else
          throw domain.types.NoSuchEntityError(
            s"Save failed for ${graph.nodeLabel} $recipeId"
          )
      }
    )

  private def attachAllToRecord(record: org.neo4j.driver.Record): Recipe = {
    val recipeMap =
      new java.util.HashMap[String, Object](record.get(graph.nodeVar).asMap())
    val userMap = record.get("createdBy").asMap()
    val tags = record.get("tags").asList().asScala.map(_.toString).toSeq
    val ingredientQuantities = record
      .get("ingredientQuantities")
      .asList((v: org.neo4j.driver.Value) => v.asMap())
      .asScala
      .map(entry => {
        val m = new java.util.HashMap[String, AnyRef]()
        val iter = entry.entrySet().iterator()
        while (iter.hasNext) {
          val e = iter.next()
          m.put(e.getKey, e.getValue)
        }
        m
      })
    recipeMap.put("createdBy", userMap)
    recipeMap.put("tags", tags)
    val iqList = new java.util.ArrayList[java.util.Map[String, AnyRef]]()
    var idx = 0
    while (idx < ingredientQuantities.size) {
      val map = ingredientQuantities(idx)
      iqList.add(map)
      idx = idx + 1
    }
    recipeMap.put("ingredientQuantities", iqList)
    RecipeConverter.toDomain(recipeMap)
  }

  override def deleteAll(): ZIO[ApiContext, Throwable, scala.Unit] = for {
    _ <- database.writeTransaction(
      s"""
       |${MatchStatement.apply}
       |DETACH DELETE ${graph.nodeVar}
       |""".stripMargin,
      (_: org.neo4j.driver.Result) => ()
    )
  } yield ()
}
