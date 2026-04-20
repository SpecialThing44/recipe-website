package persistence.ingredients

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.ingredients.Ingredient
import domain.types.NoSuchEntityError
import org.neo4j.driver.Result
import persistence.cypher.*
import persistence.filters.{CypherFragment, FiltersConverter}
import persistence.neo4j.Database
import zio.ZIO

import java.util.UUID
import scala.jdk.CollectionConverters.*

class IngredientsPersistence @Inject() (database: Database)
    extends Ingredients {
  private implicit val graph: IngredientGraph = IngredientGraph()

  private def mergeFragments(fragments: Seq[CypherFragment]): CypherFragment =
    CypherFragment(
      fragments.map(_.cypher).mkString("\n"),
      fragments.foldLeft(Map.empty[String, AnyRef])((acc, fragment) =>
        acc ++ fragment.params
      )
    )

  private def createTagStatementsFor(
      tags: Seq[String],
      includeWithUser: Boolean
  ): CypherFragment = {
    val withLine = if (includeWithUser) s"\n${WithStatement.apply}, user\n" else "\n"
    val fragments = tags.zipWithIndex.map { case (tag, index) =>
      val alias = s"tag$index"
      val tagNameParam = s"ingredient_tag_name_$index"
      val tagLowerNameParam = s"ingredient_tag_lower_name_$index"
      CypherFragment(
        s"""
           |MERGE ($alias:${graph.tagLabel} {name: $$${tagNameParam}, lowername: $$${tagLowerNameParam}})
           |CREATE (${graph.nodeVar})-[:${graph.tagRelation}]->($alias)
           |$withLine""".stripMargin,
        Map(tagNameParam -> tag, tagLowerNameParam -> tag.toLowerCase)
      )
    }
    mergeFragments(fragments)
  }

  override def list(
      query: Filters
  ): ZIO[ApiContext, Throwable, Seq[Ingredient]] = {
    val orderLine = FiltersConverter.getOrderLine(query, graph.nodeVar)
    val withLine = s"WITH ${graph.nodeVar}"
    val filterCypher = FiltersConverter.toCypher(query, graph.nodeVar)
    val pagingCypher = FiltersConverter.limitAndSkipStatement(query)
    database.readTransaction(
      s"""
         |${MatchStatement.apply}
         |${filterCypher.cypher}
         |${MatchRelationship.outgoing("CREATED_BY", "user", "User")}
         |OPTIONAL ${MatchRelationship.outgoing("HAS_TAG", "tag", "Tag")}
         |${FiltersConverter.getWithScoreLine(
          query,
          withLine
        )}, user, collect(DISTINCT tag.name) as tags
         |$orderLine
         |${pagingCypher.cypher}
         |${ReturnStatement.apply}, user as createdBy, tags
         |""".stripMargin,
      filterCypher.params ++ pagingCypher.params,
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
    val ingredientProperties = IngredientConverter.toGraph(entity).asJava

    val createTagStatements = createTagStatementsFor(
      entity.tags,
      includeWithUser = true,
    )
    val params =
      Map(
        "ingredientProperties" -> ingredientProperties,
        "createdById" -> entity.createdBy.id.toString
      ) ++ createTagStatements.params

    for {
      dbResult <- database.writeTransaction(
        s"""
             |CREATE (${graph.nodeVar}:${graph.nodeLabel})
             |SET ${graph.nodeVar} = $$ingredientProperties
             |${WithStatement.apply}
             |MATCH (user:User {id: $$createdById})
             |CREATE (${graph.nodeVar})-[:CREATED_BY]->(user)
             |${WithStatement.apply}, user
             |${createTagStatements.cypher}
             |OPTIONAL ${MatchRelationship.outgoing("HAS_TAG", "tag", "Tag")}
             |${WithStatement.apply}, user, collect(DISTINCT tag.name) as tags
             |${ReturnStatement.apply}, user as createdBy, tags
             |""".stripMargin,
        params,
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
      _ <- syncAutoWikiLinkSubstitutes(entity.id, entity.wikiLink)
    } yield dbResult
  }

  override def update(
      entity: Ingredient,
      originalEntity: Ingredient
  ): ZIO[ApiContext, Throwable, Ingredient] = {
    val ingredientProperties = IngredientConverter.toGraph(entity).asJava

    val createTagStatements = createTagStatementsFor(
      entity.tags,
      includeWithUser = false,
    )
    val params =
      Map(
        "ingredientId" -> entity.id.toString,
        "ingredientProperties" -> ingredientProperties
      ) ++ createTagStatements.params

    for {
      _ <- clearAutoWikiLinkSubstitutes(entity.id)
      updated <- database.writeTransaction(
        s"""
         |MATCH (${graph.nodeVar}:${graph.nodeLabel} {id: $$ingredientId})
         |SET ${graph.nodeVar} = $$ingredientProperties
         |${WithStatement.apply}
         |OPTIONAL MATCH (${graph.nodeVar})-[r:HAS_TAG]->()
         |DELETE r
         |${WithStatement.apply}
         |${MatchRelationship.outgoing("CREATED_BY", "user", "User")}
         |${WithStatement.apply}, user
         |${createTagStatements.cypher}
         |${WithStatement.apply}, user
         |OPTIONAL ${MatchRelationship.outgoing("HAS_TAG", "tag", "Tag")}
         |${WithStatement.apply}, user, collect(DISTINCT tag.name) as tags
         |${ReturnStatement.apply}, user as createdBy, tags
         |""".stripMargin,
        params,
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
      _ <- syncAutoWikiLinkSubstitutes(entity.id, entity.wikiLink)
    } yield updated
  }

  override def delete(id: UUID): ZIO[ApiContext, Throwable, Ingredient] =
    for {
      ingredient <- getById(id)
      dbResult <- database.writeTransaction(
        s"""
           |MATCH (${graph.nodeVar}:${graph.nodeLabel} {id: $$ingredientId})
           |${DeleteStatement.apply}
           |""".stripMargin,
        Map("ingredientId" -> id.toString),
        (_: Result) => ()
      )
    } yield ingredient

  override def getById(id: UUID): ZIO[ApiContext, Throwable, Ingredient] =
    database.readTransaction(
      s"""
         |MATCH (${graph.nodeVar}:${graph.nodeLabel} {id: $$ingredientId})
         |${MatchRelationship.outgoing("CREATED_BY", "user", "User")}
         |OPTIONAL ${MatchRelationship.outgoing("HAS_TAG", "tag", "Tag")}
         |${WithStatement.apply}, user, collect(DISTINCT tag.name) as tags
         |${ReturnStatement.apply}, user as createdBy, tags
         |""".stripMargin,
      Map("ingredientId" -> id.toString),
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

  override def listSubstitutes(
      id: UUID
  ): ZIO[ApiContext, Throwable, Seq[Ingredient]] = {
    val nodeVar = "substitute"
    database.readTransaction(
      s"""
         |MATCH (ingredient:Ingredient {id: $$ingredientId})-[:SUBSTITUTE]-(substitute:Ingredient)
         |OPTIONAL MATCH (substitute)-[:CREATED_BY]->(user:User)
         |OPTIONAL MATCH (substitute)-[:HAS_TAG]->(tag:Tag)
         |WITH $nodeVar, user, collect(DISTINCT tag.name) as tags
         |RETURN $nodeVar, user as createdBy, tags
         |ORDER BY $nodeVar.name
         |""".stripMargin,
      Map("ingredientId" -> id.toString),
      (result: Result) =>
        result.asScala
          .map(record => {
            val ingredientMap =
              new java.util.HashMap[String, Object](record.get(nodeVar).asMap())
            val userMap = record.get("createdBy").asMap()
            val tags = record.get("tags").asList().asScala.map(_.toString).toSeq
            ingredientMap.put("createdBy", userMap)
            ingredientMap.put("tags", tags)
            IngredientConverter.toDomain(ingredientMap)
          })
          .toSeq
    )
  }

  override def addSubstitute(
      id: UUID,
      substituteId: UUID
  ): ZIO[ApiContext, Throwable, Unit] =
    database.writeTransaction(
      s"""
         |MATCH (ingredient:Ingredient {id: $$ingredientId})
         |MATCH (substitute:Ingredient {id: $$substituteId})
         |MERGE (ingredient)-[:SUBSTITUTE {source: 'manual'}]-(substitute)
         |""".stripMargin,
      Map(
        "ingredientId" -> id.toString,
        "substituteId" -> substituteId.toString
      ),
      (_: Result) => ()
    )

  override def removeSubstitute(
      id: UUID,
      substituteId: UUID
  ): ZIO[ApiContext, Throwable, Unit] =
    database.writeTransaction(
      s"""
         |MATCH (ingredient:Ingredient {id: $$ingredientId})-[rel:SUBSTITUTE]-(substitute:Ingredient {id: $$substituteId})
         |DELETE rel
         |""".stripMargin,
      Map(
        "ingredientId" -> id.toString,
        "substituteId" -> substituteId.toString
      ),
      (_: Result) => ()
    )

  private def syncAutoWikiLinkSubstitutes(
      ingredientId: UUID,
      wikiLink: String
  ): ZIO[ApiContext, Throwable, Unit] =
    database.writeTransaction(
      s"""
         |MATCH (ingredient:Ingredient {id: $$ingredientId})
         |MATCH (substitute:Ingredient {wikiLink: $$wikiLink})
         |WHERE substitute.id <> ingredient.id
         |MERGE (ingredient)-[:SUBSTITUTE {source: 'wikiLink'}]-(substitute)
         |""".stripMargin,
      Map(
        "ingredientId" -> ingredientId.toString,
        "wikiLink" -> wikiLink.toLowerCase
      ),
      (_: Result) => ()
    )

  private def clearAutoWikiLinkSubstitutes(
      ingredientId: UUID
  ): ZIO[ApiContext, Throwable, Unit] =
    database.writeTransaction(
      s"""
         |MATCH (ingredient:Ingredient {id: $$ingredientId})-[rel:SUBSTITUTE]-(:Ingredient)
         |WHERE rel.source = 'wikiLink'
         |DELETE rel
         |""".stripMargin,
      Map("ingredientId" -> ingredientId.toString),
      (_: Result) => ()
    )
}
