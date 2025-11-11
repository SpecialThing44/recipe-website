package persistence.authentication

import com.google.inject.Inject
import context.ApiContext
import domain.authentication.AuthUser
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

class AuthUsersPersistence @Inject()(database: Database) extends AuthUsers {
  private implicit val graph: AuthUserGraph = AuthUserGraph()

  override def list(filters: Filters): ZIO[ApiContext, Throwable, Seq[AuthUser]] =
    database.readTransaction(
      {
        val orderLine = FiltersConverter.getOrderLine(filters, graph.nodeVar)
        val withLine = s"WITH ${graph.nodeVar}"
        s"""
               |${MatchStatement.apply} 
               |${FiltersConverter.toCypher(filters, graph.nodeVar)}
               |${FiltersConverter.getWithScoreLine(filters, withLine)}
               |$orderLine
               |${filters.limitAndSkipStatement}
               |${ReturnStatement.apply}
               |""".stripMargin
      },
      (result: Result) =>
        result.asScala
          .map(record => recordToUser(record))
          .toSeq
    )

  override def create(entity: AuthUser): ZIO[ApiContext, Throwable, AuthUser] = {
    val properties = AuthUserConverter
      .convert(entity)
    for {
      dbResult <- database.writeTransaction(
        s"""
               |CREATE (${graph.nodeVar}:${graph.nodeLabel} {
               |$properties
               |})
               |${ReturnStatement.apply}
               |""".stripMargin,
        (_: Result) => ()
      )
    } yield entity
  }

  override def update(
      entity: AuthUser,
      originalEntity: AuthUser
  ): ZIO[ApiContext, Throwable, AuthUser] = {
    val properties = AuthUserConverter
      .convertForUpdate(graph.nodeVar, entity)
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
            s"Update for ${graph.nodeLabel} with id ${entity.id} has failed for some reason"
          )
        }
      }
    )
  }

  private def recordToUser(record: org.neo4j.driver.Record): AuthUser = {
    val userMap = record.get(graph.nodeVar).asMap()
    AuthUserConverter.toDomain(userMap)
  }

  override def delete(id: UUID): ZIO[ApiContext, Throwable, AuthUser] =
    for {
      user <- getById(id)
      dbResult <- database.writeTransaction(
        s"""
               |${MatchByIdStatement.apply(id)}
               |DETACH DELETE ${graph.nodeVar}
               |""".stripMargin,
        (_: Result) => ()
      )
    } yield user

  override def getById(id: UUID): ZIO[ApiContext, Throwable, AuthUser] =
    database.readTransaction(
      s"""
               |${MatchByIdStatement.apply(id)}
               |${ReturnStatement.apply}
               |""".stripMargin,
      (result: Result) => {
        if (result.hasNext) {
          recordToUser(result.next())
        } else {
          throw NoSuchEntityError(s"${graph.nodeLabel} with id $id not found")
        }
      }
    )
}
