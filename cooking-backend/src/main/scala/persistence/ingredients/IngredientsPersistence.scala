package persistence.ingredients

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.ingredients.Ingredient
import domain.types.NoSuchEntityError
import org.neo4j.driver.{AuthTokens, Driver, GraphDatabase, Result}
import persistence.cypher.{
  DeleteStatement,
  MatchByIdStatement,
  MatchRelationship,
  MatchStatement,
  ReturnStatement,
  WithStatement
}
import persistence.filters.FiltersConverter
import persistence.neo4j.Database
import play.api.Configuration
import zio.ZIO

import java.util.UUID
import scala.jdk.CollectionConverters.*

class IngredientsPersistence @Inject() (database: Database)
    extends Ingredients {
  private implicit val graph: IngredientGraph = IngredientGraph()

  override def list(
      query: Filters
  ): ZIO[ApiContext, Throwable, Seq[Ingredient]] =
    database.readTransaction(
      s"""
         |${MatchStatement.apply}
         |${FiltersConverter.toCypher(query, graph.varName)}
         |${MatchRelationship.outgoing("CREATED_BY", "user", "User")}
         |OPTIONAL ${MatchRelationship.outgoing("HAS_TAG", "tag", "Tag")}
         |${WithStatement.apply}, user, collect(labels(tag)[1]) as tags
         |${ReturnStatement.apply}, user as createdBy, tags
         |""".stripMargin,
      (result: Result) =>
        result.asScala
          .map(record => {
            val ingredientMap = new java.util.HashMap[String, Object](
              record.get(graph.varName).asMap()
            )
            val userMap = record.get("createdBy").asMap()
            val tags = record.get("tags").asList().asScala.map(_.toString).toSeq
            ingredientMap.put("createdBy", userMap)
            ingredientMap.put("tags", tags)
            IngredientConverter.toDomain(ingredientMap)
          })
          .toSeq
    )

  override def create(
      entity: Ingredient
  ): ZIO[ApiContext, Throwable, Ingredient] = {
    val properties = IngredientConverter
      .convert(entity)

    val createTagStatements = entity.tags
      .map(tag => s"""
         |MERGE (tag:Tag:$tag)
         |CREATE (${graph.varName})-[:HAS_TAG]->(tag)
         |${WithStatement.apply}, user
         |""".stripMargin)
      .mkString("\n")
    for {
      dbResult <- database.writeTransaction(
        s"""
             |CREATE (${graph.varName}:${graph.nodeName} {
             |$properties
             |})
             |${WithStatement.apply}
             |MATCH (user:User {id: '${entity.createdBy.id}'})
             |CREATE (${graph.varName})-[:CREATED_BY]->(user)
             |${WithStatement.apply}, user
             |$createTagStatements
             |OPTIONAL ${MatchRelationship.outgoing("HAS_TAG", "tag", "Tag")}
             |${WithStatement.apply}, user, collect(labels(tag)[1]) as tags
             |${ReturnStatement.apply}, user as createdBy, tags
             |""".stripMargin,
        (result: Result) => {
          if (result.hasNext) {
            val record = result.next()
            val ingredientMap = new java.util.HashMap[String, Object](
              record.get(graph.varName).asMap()
            )
            val userMap = record.get("createdBy").asMap()
            val tags = record.get("tags").asList().asScala.map(_.toString).toSeq
            ingredientMap.put("createdBy", userMap)
            ingredientMap.put("tags", tags)
            IngredientConverter.toDomain(ingredientMap)
          } else {
            throw NoSuchEntityError(
              s"Create for ${graph.nodeName} has failed for some reason"
            )
          }
        }
      )
    } yield dbResult
  }

  override def update(
      entity: Ingredient,
      originalEntity: Ingredient
  ): ZIO[ApiContext, Throwable, Ingredient] = {
    val properties = IngredientConverter
      .convertForUpdate(graph.varName, entity)

    val createTagStatements = entity.tags
      .map(tag => s"""
         |MERGE (tag:Tag:$tag)
         |CREATE (${graph.varName})-[:HAS_TAG]->(tag)
         |""".stripMargin)
      .mkString("\n")

    database.writeTransaction(
      s"""
         |${MatchByIdStatement.apply(entity.id)}
         |SET $properties
         |${WithStatement.apply}
         |OPTIONAL MATCH (${graph.varName})-[r:HAS_TAG]->()
         |DELETE r
         |${WithStatement.apply}
         |${MatchRelationship.outgoing("CREATED_BY", "user", "User")}
         |${WithStatement.apply}, user
         |$createTagStatements
         |${WithStatement.apply}, user
         |OPTIONAL ${MatchRelationship.outgoing("HAS_TAG", "tag", "Tag")}
         |${WithStatement.apply}, user, collect(labels(tag)[1]) as tags
         |${ReturnStatement.apply}, user as createdBy, tags
         |""".stripMargin,
      (result: Result) => {
        if (result.hasNext) {
          val record = result.next()
          val ingredientMap = new java.util.HashMap[String, Object](
            record.get(graph.varName).asMap()
          )
          val userMap = record.get("createdBy").asMap()
          val tags = record.get("tags").asList().asScala.map(_.toString).toSeq
          ingredientMap.put("createdBy", userMap)
          ingredientMap.put("tags", tags)
          IngredientConverter.toDomain(ingredientMap)
        } else {
          throw NoSuchEntityError(
            s"Update for ${graph.nodeName} with id ${entity.id} has failed for some reason"
          )
        }
      }
    )
  }

  override def delete(id: UUID): ZIO[ApiContext, Throwable, Ingredient] =
    for {
      ingredient <- getById(id)
      dbResult <- database.writeTransaction(
        s"""
           |${MatchByIdStatement.apply(id)}
           |${DeleteStatement.apply}
           |""".stripMargin,
        (_: Result) => ()
      )
    } yield ingredient

  override def getById(id: UUID): ZIO[ApiContext, Throwable, Ingredient] =
    database.readTransaction(
      s"""
         |${MatchByIdStatement.apply(id)}
         |${MatchRelationship.outgoing("CREATED_BY", "user", "User")}
         |OPTIONAL ${MatchRelationship.outgoing("HAS_TAG", "tag", "Tag")}
         |${WithStatement.apply}, user, collect(labels(tag)[1]) as tags
         |${ReturnStatement.apply}, user as createdBy, tags
         |""".stripMargin,
      (result: Result) => {
        if (result.hasNext) {
          val record = result.next()
          val ingredientMap = new java.util.HashMap[String, Object](
            record.get(graph.varName).asMap()
          )
          val userMap = record.get("createdBy").asMap()
          val tags = record.get("tags").asList().asScala.map(_.toString).toSeq
          ingredientMap.put("createdBy", userMap)
          ingredientMap.put("tags", tags)
          IngredientConverter.toDomain(ingredientMap)
        } else {
          throw NoSuchEntityError(s"${graph.nodeName} with id $id not found")
        }
      }
    )
}
