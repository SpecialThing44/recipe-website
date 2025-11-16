package persistence.authentication

import com.google.inject.Inject
import context.ApiContext
import domain.authentication.RefreshToken
import org.neo4j.driver.Result
import persistence.cypher.{MatchByIdStatement, MatchStatement, ReturnStatement, WithStatement}
import persistence.neo4j.Database
import zio.ZIO

import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters.*

class RefreshTokensPersistence @Inject() (database: Database) extends RefreshTokens {
  private implicit val graph: RefreshTokenGraph = RefreshTokenGraph()

  override def create(token: RefreshToken): ZIO[ApiContext, Throwable, RefreshToken] = {
    val properties = RefreshTokenConverter.convert(token)
    database.writeTransaction(
      s"""
         |CREATE (${graph.nodeVar}:${graph.nodeLabel} {
         |$properties
         |})
         |${ReturnStatement.apply}
         |""".stripMargin,
      (result: Result) => {
        if (result.hasNext) {
          recordToToken(result.next())
        } else {
          throw new Exception(s"Create for ${graph.nodeLabel} has failed")
        }
      }
    )
  }

  override def getByToken(tokenString: String): ZIO[ApiContext, Throwable, Option[RefreshToken]] =
    database.readTransaction(
      s"""
         |${MatchStatement.apply} WHERE ${graph.nodeVar}.token = '$tokenString'
         |${ReturnStatement.apply}
         |""".stripMargin,
      (result: Result) => {
        if (result.hasNext) {
          Some(recordToToken(result.next()))
        } else {
          None
        }
      }
    )

  override def revokeToken(tokenId: UUID): ZIO[ApiContext, Throwable, Unit] =
    database.writeTransaction(
      s"""
         |${MatchByIdStatement.apply(tokenId)}
         |SET ${graph.nodeVar}.isRevoked = 'true'
         |""".stripMargin,
      (_: Result) => ()
    )

  override def revokeAllForUser(userId: UUID): ZIO[ApiContext, Throwable, Unit] =
    database.writeTransaction(
      s"""
         |${MatchStatement.apply} WHERE ${graph.nodeVar}.userId = '$userId'
         |SET ${graph.nodeVar}.isRevoked = 'true'
         |""".stripMargin,
      (_: Result) => ()
    )

  override def deleteAllForUser(userId: UUID): ZIO[ApiContext, Throwable, Unit] =
    database.writeTransaction(
      s"""
         |${MatchStatement.apply} WHERE ${graph.nodeVar}.userId = '$userId'
         |DETACH DELETE ${graph.nodeVar}
         |""".stripMargin,
      (_: Result) => ()
    )

  override def deleteExpired(): ZIO[ApiContext, Throwable, Unit] = {
    val now = Instant.now.toString
    database.writeTransaction(
      s"""
         |${MatchStatement.apply} WHERE ${graph.nodeVar}.expiresAt < '$now'
         |DETACH DELETE ${graph.nodeVar}
         |""".stripMargin,
      (_: Result) => ()
    )
  }

  private def recordToToken(record: org.neo4j.driver.Record): RefreshToken = {
    val tokenMap = record.get(graph.nodeVar).asMap()
    RefreshTokenConverter.toDomain(tokenMap)
  }
}
