package persistence.users

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.types.NoSuchEntityError
import domain.users.User
import org.neo4j.driver.Result
import persistence.cypher.{MatchStatement, ReturnStatement}
import persistence.filters.{CypherFragment, FiltersConverter}
import persistence.neo4j.Database
import zio.ZIO

import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters.{IteratorHasAsScala, MapHasAsJava}

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

  override def list(filters: Filters): ZIO[ApiContext, Throwable, Seq[User]] = {
    val orderLine = CypherFragment.getOrderLine(filters, graph.nodeVar)
    val withLine = s"WITH ${graph.nodeVar}"
    val filterCypher = FiltersConverter.toCypher(filters, graph.nodeVar)
    val pagingCypher = CypherFragment.limitAndSkipStatement(filters)
    database.readTransaction(
      s"""
         |${MatchStatement.apply} WHERE NOT (user:DeletedUser)
         |${MatchStatement.apply}
         |${filterCypher.cypher}
         |${CypherFragment.getWithScoreLine(filters, withLine)}
         |$orderLine
         |${pagingCypher.cypher}
         |${ReturnStatement.apply}
         |""".stripMargin,
      filterCypher.params ++ pagingCypher.params,
      (result: Result) =>
        result.asScala
          .map(record => recordToUser(record))
          .toSeq
    )
  }

  override def create(entity: User): ZIO[ApiContext, Throwable, User] = {
    val userProperties = UserConverter.toGraph(entity).asJava
    for {
      dbResult <- database.writeTransaction(
        s"""
           |CREATE (${graph.nodeVar}:${graph.nodeLabel})
           |SET ${graph.nodeVar} = $$userProperties
           |${ReturnStatement.apply}
           |""".stripMargin,
        Map("userProperties" -> userProperties),
        (_: Result) => ()
      )
    } yield entity
  }

  override def update(
      entity: User,
      originalEntity: User
  ): ZIO[ApiContext, Throwable, User] = {
    val userProperties = UserConverter.toGraph(entity).asJava
    database.writeTransaction(
      s"""
         |MATCH (${graph.nodeVar}:${graph.nodeLabel} {id: $$userId})
         |SET ${graph.nodeVar} = $$userProperties
         |${ReturnStatement.apply}
         |""".stripMargin,
      Map(
        "userId" -> entity.id.toString,
        "userProperties" -> userProperties
      ),
      (result: Result) => {
        if (result.hasNext) {
          recordToUser(result.next())
        } else {
          throw NoSuchEntityError(
            s"Update for ${graph.nodeLabel} with id ${entity.id} has failed for some reason"
          )
        }
      }
    )
  }

  private def recordToUser(record: org.neo4j.driver.Record): User = {
    val userMap = record.get(graph.nodeVar).asMap()
    UserConverter.toDomain(userMap)
  }

  override def delete(id: UUID): ZIO[ApiContext, Throwable, User] =
    for {
      user <- getById(id)
      now = Instant.now().toString
      dbResult <- database.writeTransaction(
        s"""
           |MATCH (${graph.nodeVar}:${graph.nodeLabel} {id: $$userId})
           |$deletePrivateRecipesForUser
           |WITH DISTINCT user
           |$deleteUnusedRecipesCreatedByUser
           |WITH DISTINCT user
           |$deleteUnusedIngredientsCreatedByUser
           |WITH DISTINCT user
           |SET user.email = ""
           |SET user.name = "Deleted User"
           |SET user.updatedOn = $$updatedOn
           |SET user:DeletedUser
           |""".stripMargin,
        Map(
          "userId" -> id.toString,
          "updatedOn" -> now
        ),
        (_: Result) => ()
      )
    } yield user

  override def getById(id: UUID): ZIO[ApiContext, Throwable, User] =
    database.readTransaction(
      s"""
         |MATCH (${graph.nodeVar}:${graph.nodeLabel} {id: $$userId})
         |${ReturnStatement.apply}
         |""".stripMargin,
      Map("userId" -> id.toString),
      (result: Result) => {
        if (result.hasNext) {
          recordToUser(result.next())
        } else {
          throw NoSuchEntityError(s"${graph.nodeLabel} with id $id not found")
        }
      }
    )

  override def deleteAll(): ZIO[ApiContext, Throwable, Unit] = for {
    _ <- database.writeTransaction(
      s"""
       |${MatchStatement.apply()}
       |DETACH DELETE user
       |""".stripMargin,
      (_: Result) => ()
    )
  } yield ()

  override def makeAdmin(userId: UUID): ZIO[ApiContext, Throwable, User] = {
    val now = Instant.now().toString
    database.writeTransaction(
      s"""
         |MATCH (${graph.nodeVar}:${graph.nodeLabel} {id: $$userId})
         |SET user.admin = true
         |SET user.updatedOn = $$updatedOn
         |${ReturnStatement.apply}
         |""".stripMargin,
      Map(
        "userId" -> userId.toString,
        "updatedOn" -> now
      ),
      (result: Result) => {
        if (result.hasNext) {
          recordToUser(result.next())
        } else {
          throw NoSuchEntityError(
            s"User with id $userId not found"
          )
        }
      }
    )
  }

  override def getByIdentity(
      identity: String
  ): ZIO[ApiContext, Throwable, Option[User]] =
    database.readTransaction(
      s"""
         |${MatchStatement.apply}
         |WHERE ${graph.nodeVar}.identity = $$identity
         |${ReturnStatement.apply}
         |""".stripMargin,
      Map("identity" -> identity),
      (result: Result) => {
        if (result.hasNext) {
          Some(recordToUser(result.next()))
        } else {
          None
        }
      }
    )
}
