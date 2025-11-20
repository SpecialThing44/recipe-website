package persistence.ingredients

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.ingredients.Ingredient
import domain.types.NoSuchEntityError
import org.neo4j.driver.Result
import persistence.cypher.*
import persistence.filters.FiltersConverter
import persistence.neo4j.Database
import zio.ZIO

import java.util.UUID
import scala.jdk.CollectionConverters.*

class IngredientsPersistence @Inject() (database: Database)
    extends Ingredients {
  private implicit val graph: IngredientGraph = IngredientGraph()

  override def list(
      query: Filters
  ): ZIO[ApiContext, Throwable, Seq[Ingredient]] = {
    val orderLine = FiltersConverter.getOrderLine(query, graph.nodeVar)
    val withLine = s"WITH ${graph.nodeVar}"
    database.readTransaction(
      s"""
         |${MatchStatement.apply}
         |${FiltersConverter.toCypher(query, graph.nodeVar)}
         |${MatchRelationship.outgoing("CREATED_BY", "user", "User")}
         |OPTIONAL ${MatchRelationship.outgoing("HAS_TAG", "tag", "Tag")}
         |${FiltersConverter.getWithScoreLine(query, withLine)}, user, collect(DISTINCT tag.name) as tags
         |$orderLine
         |${query.limitAndSkipStatement}
         |${ReturnStatement.apply}, user as createdBy, tags
         |""".stripMargin,
      (result: Result) =>
        result.asScala
          .map(record => {
            attachUserAndTagsToRecord(record)
          })
          .toSeq
    )
  }

  private def attachUserAndTagsToRecord(
      record: org.neo4j.driver.Record
  ): Ingredient = {
    val ingredientMap = new java.util.HashMap[String, Object](
      record.get(graph.nodeVar).asMap()
    )
    val userMap = record.get("createdBy").asMap()
    val tags = record.get("tags").asList().asScala.map(_.toString).toSeq
    ingredientMap.put("createdBy", userMap)
    ingredientMap.put("tags", tags)
    IngredientConverter.toDomain(ingredientMap)
  }

  override def create(
      entity: Ingredient
  ): ZIO[ApiContext, Throwable, Ingredient] = {
    val properties = IngredientConverter
      .convert(entity)

    val createTagStatements = graph.createTagStatementsFor(graph.nodeVar, graph.tagRelation, graph.tagLabel, entity.tags, includeWithUser = true, useAliasSuffix = false)
    for {
      dbResult <- database.writeTransaction(
        s"""
             |CREATE (${graph.nodeVar}:${graph.nodeLabel} {
             |$properties
             |})
             |${WithStatement.apply}
             |MATCH (user:User {id: '${entity.createdBy.id}'})
             |CREATE (${graph.nodeVar})-[:CREATED_BY]->(user)
             |${WithStatement.apply}, user
             |$createTagStatements
             |OPTIONAL ${MatchRelationship.outgoing("HAS_TAG", "tag", "Tag")}
             |${WithStatement.apply}, user, collect(DISTINCT tag.name) as tags
             |${ReturnStatement.apply}, user as createdBy, tags
             |""".stripMargin,
        (result: Result) => {
          if (result.hasNext) {
            attachUserAndTagsToRecord(result.next())
          } else {
            throw NoSuchEntityError(
              s"Create for ${graph.nodeLabel} has failed for some reason"
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
      .convertForUpdate(graph.nodeVar, entity)

    val createTagStatements = graph.createTagStatementsFor(graph.nodeVar, graph.tagRelation, graph.tagLabel, entity.tags, includeWithUser = false, useAliasSuffix = true)

    database.writeTransaction(
      s"""
         |${MatchByIdStatement.apply(entity.id)}
         |SET $properties
         |${WithStatement.apply}
         |OPTIONAL MATCH (${graph.nodeVar})-[r:HAS_TAG]->()
         |DELETE r
         |${WithStatement.apply}
         |${MatchRelationship.outgoing("CREATED_BY", "user", "User")}
         |${WithStatement.apply}, user
         |$createTagStatements
         |${WithStatement.apply}, user
         |OPTIONAL ${MatchRelationship.outgoing("HAS_TAG", "tag", "Tag")}
         |${WithStatement.apply}, user, collect(DISTINCT tag.name) as tags
         |${ReturnStatement.apply}, user as createdBy, tags
         |""".stripMargin,
      (result: Result) => {
        if (result.hasNext) {
          attachUserAndTagsToRecord(result.next())
        } else {
          throw NoSuchEntityError(
            s"Update for ${graph.nodeLabel} with id ${entity.id} has failed for some reason"
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
         |${WithStatement.apply}, user, collect(DISTINCT tag.name) as tags
         |${ReturnStatement.apply}, user as createdBy, tags
         |""".stripMargin,
      (result: Result) => {
        if (result.hasNext) {
          attachUserAndTagsToRecord(result.next())
        } else {
          throw NoSuchEntityError(s"${graph.nodeLabel} with id $id not found")
        }
      }
    )

  override def deleteAll(): ZIO[ApiContext, Throwable, Unit] = for {
    _ <- database.writeTransaction(
      s"""
       |${MatchStatement.apply}
       |DETACH DELETE ${graph.nodeVar}
       |""".stripMargin,
      (_: Result) => ()
    )
  } yield ()
}
