package persistence.recipes

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.recipes.Recipe
import persistence.cypher.*
import persistence.filters.FiltersConverter
import persistence.neo4j.Database
import zio.ZIO

import java.util.UUID
import scala.jdk.CollectionConverters.*

class RecipePersistence @Inject() (database: Database) extends Recipes {
  private implicit val graph: RecipeGraph = RecipeGraph()

  override def list(query: Filters): ZIO[ApiContext, Throwable, Seq[Recipe]] =
    database.readTransaction(
      s"""
         |${MatchStatement.apply}
         |${FiltersConverter.toCypher(query, graph.varName)}
         |${MatchRelationship.outgoing("CREATED_BY", "user", "User")}
         |OPTIONAL MATCH (${graph.varName})-[ri:HAS_INGREDIENT]->(ingredient:Ingredient)
         |OPTIONAL ${MatchRelationship.outgoing("HAS_TAG", "tag", "Tag")}
         |WITH ${graph.varName}, user, collect(DISTINCT tag.name) as tags, collect(DISTINCT ingredient) as ingredients, collect(DISTINCT ri.amount) as amounts, collect(DISTINCT ri.unit) as units
         |${ReturnStatement.apply}, user as createdBy, tags, ingredients, amounts, units
         |""".stripMargin,
      (result: org.neo4j.driver.Result) =>
        result.asScala
          .map(record => attachAllToRecord(record))
          .toSeq
    )

  override def create(entity: Recipe): ZIO[ApiContext, Throwable, Recipe] = {
    val properties = RecipeConverter.convert(entity)
    val createTagStatements = entity.tags
      .map(tag => s"""
         |MERGE (tag:Tag {name: '%s', lowername: '%s'})
         |CREATE (${graph.varName})-[:HAS_TAG]->(tag)
         |${WithStatement.apply}, user
         |""".stripMargin.format(tag, tag, tag.toLowerCase))
      .mkString("\n")

    val createIngredientStatements = entity.ingredients
      .map(ii => s"""
         |MATCH (ing:Ingredient {id: '%s'})
         |CREATE (${graph.varName})-[:HAS_INGREDIENT {amount: %d, unit: '%s'}]->(ing)
         |${WithStatement.apply}, user
         |""".stripMargin.format(ii.ingredient.id.toString, ii.quantity.amount, ii.quantity.unit.name))
      .mkString("\n")

    database.writeTransaction(
      s"""
         |CREATE (${graph.varName}:${graph.nodeName} {
         |$properties
         |})
         |${WithStatement.apply}
         |MATCH (user:User {id: '${entity.createdBy.id}'})
         |CREATE (${graph.varName})-[:CREATED_BY]->(user)
         |${WithStatement.apply}, user
         |$createTagStatements
         |$createIngredientStatements
         |OPTIONAL MATCH (${graph.varName})-[ri:HAS_INGREDIENT]->(ingredient:Ingredient)
         |OPTIONAL ${MatchRelationship.outgoing("HAS_TAG", "tag", "Tag")}
         |WITH ${graph.varName}, user, collect(DISTINCT tag.name) as tags, collect(DISTINCT ingredient) as ingredients, collect(ri.amount) as amounts, collect(ri.unit) as units
         |${ReturnStatement.apply}, user as createdBy, tags, ingredients, amounts, units
         |""".stripMargin,
      (result: org.neo4j.driver.Result) => {
        if (result.hasNext) {
          attachAllToRecord(result.next())
        } else {
          throw domain.types.NoSuchEntityError(s"Create for ${graph.nodeName} has failed for some reason")
        }
      }
    )
  }

  override def update(
      entity: Recipe,
      originalEntity: Recipe
  ): ZIO[ApiContext, Throwable, Recipe] = {
    val properties = RecipeConverter.convertForUpdate(graph.varName, entity)
    val createTagStatements = entity.tags
      .map(tag => s"""
         |MERGE (tag%s:Tag {name: '%s', lowername: '%s'})
         |CREATE (${graph.varName})-[:HAS_TAG]->(tag%s)
         |${WithStatement.apply}, user
         |""".stripMargin.format(tag, tag, tag, tag.toLowerCase, tag))
      .mkString("\n")

    val createIngredientStatements = entity.ingredients
      .map(ii => s"""
         |MATCH (ing%s:Ingredient {id: '%s'})
         |CREATE (${graph.varName})-[:HAS_INGREDIENT {amount: %d, unit: '%s'}]->(ing%s)
         |${WithStatement.apply}, user
         |""".stripMargin.format(ii.ingredient.id.toString.replace("-", ""), ii.ingredient.id.toString, ii.quantity.amount, ii.quantity.unit.name, ii.ingredient.id.toString.replace("-", "")))
      .mkString("\n")

    database.writeTransaction(
      s"""
         |${MatchByIdStatement.apply(entity.id)}
         |SET $properties
         |${WithStatement.apply}
         |OPTIONAL MATCH (${graph.varName})-[r:HAS_TAG]->()
         |DELETE r
         |${WithStatement.apply}
         |OPTIONAL MATCH (${graph.varName})-[ri:HAS_INGREDIENT]->()
         |DELETE ri
         |${WithStatement.apply}
         |${MatchRelationship.outgoing("CREATED_BY", "user", "User")}
         |${WithStatement.apply}, user
         |$createTagStatements
         |$createIngredientStatements
         |${WithStatement.apply}, user
         |OPTIONAL MATCH (${graph.varName})-[ri:HAS_INGREDIENT]->(ingredient:Ingredient)
         |OPTIONAL ${MatchRelationship.outgoing("HAS_TAG", "tag", "Tag")}
         |WITH ${graph.varName}, user, collect(DISTINCT tag.name) as tags, collect(DISTINCT ingredient) as ingredients, collect(ri.amount) as amounts, collect(ri.unit) as units
         |${ReturnStatement.apply}, user as createdBy, tags, ingredients, amounts, units
         |""".stripMargin,
      (result: org.neo4j.driver.Result) => {
        if (result.hasNext) {
          attachAllToRecord(result.next())
        } else {
          throw domain.types.NoSuchEntityError(s"Update for ${graph.nodeName} with id ${entity.id} has failed for some reason")
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
         |OPTIONAL MATCH (${graph.varName})-[ri:HAS_INGREDIENT]->(ingredient:Ingredient)
         |OPTIONAL ${MatchRelationship.outgoing("HAS_TAG", "tag", "Tag")}
         |WITH ${graph.varName}, user, collect(DISTINCT tag.name) as tags, collect(DISTINCT ingredient) as ingredients, collect(ri.amount) as amounts, collect(ri.unit) as units
         |${ReturnStatement.apply}, user as createdBy, tags, ingredients, amounts, units
         |""".stripMargin,
      (result: org.neo4j.driver.Result) => {
        if (result.hasNext) {
          attachAllToRecord(result.next())
        } else {
          throw domain.types.NoSuchEntityError(s"${graph.nodeName} with id $id not found")
        }
      }
    )

  override def save(recipeId: UUID, userId: UUID): ZIO[ApiContext, Throwable, Recipe] =
    database.writeTransaction(
      s"""
         |MATCH (${graph.varName}:${graph.nodeName} {id: '$recipeId'})
         |MATCH (user:User {id: '$userId'})
         |MERGE (${graph.varName})-[:SAVED_BY]->(user)
         |${WithStatement.apply}
         |${MatchRelationship.outgoing("CREATED_BY", "created", "User")}
         |WITH ${graph.varName}, created as user
         |OPTIONAL MATCH (${graph.varName})-[ri:HAS_INGREDIENT]->(ingredient:Ingredient)
         |OPTIONAL ${MatchRelationship.outgoing("HAS_TAG", "tag", "Tag")}
         |WITH ${graph.varName}, user, collect(DISTINCT tag.name) as tags, collect(DISTINCT ingredient) as ingredients, collect(ri.amount) as amounts, collect(ri.unit) as units
         |${ReturnStatement.apply}, user as createdBy, tags, ingredients, amounts, units
         |""".stripMargin,
      (result: org.neo4j.driver.Result) => {
        if (result.hasNext) attachAllToRecord(result.next())
        else throw domain.types.NoSuchEntityError(s"Save failed for ${graph.nodeName} $recipeId")
      }
    )

  private def attachAllToRecord(record: org.neo4j.driver.Record): Recipe = {
    val recipeMap = new java.util.HashMap[String, Object](record.get(graph.varName).asMap())
    val userMap = record.get("createdBy").asMap()
    val tags = record.get("tags").asList().asScala.map(_.toString).toSeq
    val ingredients = record
      .get("ingredients")
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
    val amounts = record.get("amounts").asList().asScala.map(_.toString.toInt).toSeq
    val units = record.get("units").asList().asScala.map(_.toString).toSeq
    recipeMap.put("createdBy", userMap)
    recipeMap.put("tags", tags)
    val ingredientMaps = new java.util.ArrayList[java.util.Map[String, AnyRef]]()
    var idx = 0
    while (idx < ingredients.size) {
      val map = ingredients(idx)
      ingredientMaps.add(map)
      idx = idx + 1
    }
    recipeMap.put("ingredients", ingredientMaps)
    recipeMap.put("amounts", amounts.asJava)
    recipeMap.put("units", units.asJava)
    RecipeConverter.toDomain(recipeMap)
  }
}
