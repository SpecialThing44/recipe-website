package persistence.users

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.types.NoSuchEntityError
import domain.users.User
import org.neo4j.driver.Result
import persistence.cypher.{MatchByIdStatement, MatchStatement, ReturnStatement}
import persistence.filters.FiltersConverter
import persistence.neo4j.Database
import zio.ZIO

import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters.IteratorHasAsScala

class UsersPersistence @Inject() (database: Database) extends Users {
  private implicit val graph: UserGraph = UserGraph()
  private val deletePrivateRecipesForUser =
    s"""
       |OPTIONAL MATCH (privateRecipe:Recipe)-[CREATED_BY]->(user) WHERE privateRecipe.private = true
       |DETACH DELETE privateRecipe""".stripMargin
  private val deleteUnusedRecipesCreatedByUser =
    s"""
       |OPTIONAL MATCH (unusedRecipe:Recipe)-[CREATED_BY]->(user)
       |WHERE NOT (unusedRecipe)<-[:SAVED_BY]-(:User)
       |DETACH DELETE unusedRecipe
       """.stripMargin
  private val deleteUnusedIngredientsCreatedByUser =
    s"""
       |OPTIONAL MATCH (user)<-[:CREATED_BY]-(ingredient:Ingredient)
       |WHERE NOT (ingredient)<-[:USES]-(:Recipe)
       |DETACH DELETE ingredient""".stripMargin

  override def list(filters: Filters): ZIO[ApiContext, Throwable, Seq[User]] =
    database.readTransaction(
      s"""
               |${MatchStatement.apply}
               |${FiltersConverter.toCypher(filters, graph.varName)}
               |${ReturnStatement.apply}
               |""".stripMargin,
      (result: Result) =>
        result.asScala
          .map(record => recordToUser(record))
          .toSeq
    )

  override def create(entity: User): ZIO[ApiContext, Throwable, User] = {
    val properties = UserConverter
      .convert(entity)
    for {
      dbResult <- database.writeTransaction(
        s"""
               |CREATE (${graph.varName}:${graph.nodeName} {
               |$properties
               |})
               |${ReturnStatement.apply}
               |""".stripMargin,
        (_: Result) => ()
      )
    } yield entity
  }

  override def update(
      entity: User,
      originalEntity: User
  ): ZIO[ApiContext, Throwable, User] = {
    val properties = UserConverter
      .convertForUpdate(graph.varName, entity)
    database.writeTransaction(
      s"""
               |${MatchByIdStatement.apply(entity.id)}
               |SET $properties
               |${ReturnStatement.apply}
               |""".stripMargin,
      (result: Result) => {
        if (result.hasNext) {
          recordToUser(result.next())
        } else {
          throw NoSuchEntityError(
            s"Update for ${graph.nodeName} with id ${entity.id} has failed for some reason"
          )
        }
      }
    )
  }

  private def recordToUser(record: org.neo4j.driver.Record): User = {
    val userMap = record.get(graph.varName).asMap()
    UserConverter.toDomain(userMap)
  }

  override def delete(id: UUID): ZIO[ApiContext, Throwable, User] =
    for {
      user <- getById(id)
      dbResult <- database.writeTransaction(
        s"""
               |${MatchByIdStatement.apply(id)}
               |$deletePrivateRecipesForUser
               |WITH DISTINCT user
               |$deleteUnusedRecipesCreatedByUser
               |WITH DISTINCT user
               |$deleteUnusedIngredientsCreatedByUser
               |WITH DISTINCT user
               |SET user.email = ""
               |SET user.name = "Deleted User"
               |SET user.updatedOn = "${Instant.now.toString}"
               |SET user:DeletedUser
               |""".stripMargin,
        (_: Result) => ()
      )
    } yield user

  override def getById(id: UUID): ZIO[ApiContext, Throwable, User] =
    database.readTransaction(
      s"""
               |${MatchByIdStatement.apply(id)}
               |${ReturnStatement.apply}
               |""".stripMargin,
      (result: Result) => {
        if (result.hasNext) {
          recordToUser(result.next())
        } else {
          throw NoSuchEntityError(s"${graph.nodeName} with id $id not found")
        }
      }
    )

  override def authenticate(
      email: String,
  ): ZIO[ApiContext, Throwable, User] =
    database.readTransaction(
      s"""
               |MATCH (${graph.varName}:${graph.nodeName} {email: '$email'})
               |${ReturnStatement.apply}
               |""".stripMargin,
      (result: Result) => {
        if (result.hasNext) {
          val userMap = result.next().get(graph.varName).asMap()
          UserConverter.toAuthDomain(userMap)
        } else {
          throw NoSuchEntityError(
            s"${graph.nodeName} with email $email not found"
          )
        }
      }
    )

  override def getByIdWithPassword(id: UUID): ZIO[ApiContext, Throwable, User] =
    database.readTransaction(
      s"""
               |${MatchByIdStatement.apply(id)}
               |${ReturnStatement.apply}
               |""".stripMargin,
      (result: Result) => {
        if (result.hasNext) {
          val userMap = result.next().get(graph.varName).asMap()
          UserConverter.toAuthDomain(userMap)
        } else {
          throw NoSuchEntityError(s"${graph.nodeName} with id $id not found")
        }
      }
    )
}
