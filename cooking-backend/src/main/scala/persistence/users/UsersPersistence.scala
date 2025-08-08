package persistence.users

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.people.users.User
import domain.types.NoSuchEntityError
import org.neo4j.driver.{AuthTokens, Driver, GraphDatabase, Session}
import persistence.cypher.{DeleteStatement, MatchByIdStatement, ReturnStatement}
import play.api.Configuration
import zio.ZIO

import java.util.UUID
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.util.Try

class UsersPersistence @Inject() (config: Configuration) extends Users {

  private val uri = config.get[String]("neo4j.uri")
  private val username = config.get[String]("neo4j.username")
  private val password = config.get[String]("neo4j.password")
  private val driver: Driver =
    GraphDatabase.driver(uri, AuthTokens.basic(username, password))
  private implicit val graph: UserGraph = UserGraph()

  override def list(query: Filters): ZIO[ApiContext, Throwable, Seq[User]] =
    ZIO.fromTry {
      Try {
        val session: Session = driver.session()
        try {
          val query =
            s"""
               |MATCH (${graph.varName}:${graph.nodeName})
               |${ReturnStatement.apply}
               |""".stripMargin
          val result = session.run(query)
          result.asScala
            .map(record => record.get(graph.varName).asMap())
            .map(UserConverter.toDomain)
            .toSeq

        } finally {
          session.close()
        }
      }
    }

  override def create(entity: User): ZIO[ApiContext, Throwable, User] =
    ZIO.fromTry {
      Try {
        val session: Session = driver.session()
        try {
          val properties = UserConverter
            .convert(entity)
          val query =
            s"""
               |CREATE (${graph.varName}:${graph.nodeName} {
               |$properties
               |})
               |${ReturnStatement.apply}
               |""".stripMargin
          session.run(query)
          entity
        } finally {
          session.close()
        }
      }
    }

  override def update(
      entity: User,
      originalEntity: User
  ): ZIO[ApiContext, Throwable, User] =
    ZIO.fromTry {
      Try {
        val session: Session = driver.session()
        try {
          val properties = UserConverter
            .convertForUpdate(graph.varName, entity)
          val query =
            s"""
               |${MatchByIdStatement.apply(entity.id)}
               |SET $properties
               |${ReturnStatement.apply}
               |""".stripMargin
          val result = session.run(query)
          if (result.hasNext) {
            val record = result.next().get(graph.varName).asMap()
            UserConverter.toDomain(record)
          } else {
            throw NoSuchEntityError(
              s"Update for ${graph.nodeName} with id ${entity.id} has failed for some reason"
            )
          }
        } finally {
          session.close()
        }
      }
    }

  override def delete(id: UUID): ZIO[ApiContext, Throwable, User] =
    for {
      user <- getById(id)
      _ <- ZIO.fromTry {
        Try {
          val session: Session = driver.session()
          try {
            val query =
              s"""
                 |${MatchByIdStatement.apply(id)}
                 |${DeleteStatement.apply}
                 |""".stripMargin
            session.run(query)
          } finally {
            session.close()
          }
        }
      }
    } yield user

  override def getById(id: UUID): ZIO[ApiContext, Throwable, User] =
    ZIO.fromTry {
      Try {
        val session: Session = driver.session()
        try {
          val query =
            s"""
               |${MatchByIdStatement.apply(id)}
               |${ReturnStatement.apply}
               |""".stripMargin
          val result = session.run(query)
          if (result.hasNext) {
            val record = result.next().get(graph.varName).asMap()
            UserConverter.toDomain(record)
          } else {
            throw NoSuchEntityError(s"${graph.nodeName} with id $id not found")
          }
        } finally {
          session.close()
        }
      }
    }

  override def authenticate(
      email: String,
  ): ZIO[ApiContext, Throwable, User] =
    ZIO.fromTry {
      Try {
        val session: Session = driver.session()
        try {
          val query =
            s"""
               |MATCH (${graph.varName}:${graph.nodeName} {email: '$email'})
               |${ReturnStatement.apply}
               |""".stripMargin
          val result = session.run(query)
          if (result.hasNext) {
            val record = result.next().get(graph.varName).asMap()
            UserConverter.toAuthDomain(record)
          } else {
            throw NoSuchEntityError(
              s"${graph.nodeName} with email $email not found"
            )
          }
        } finally {
          session.close()
        }
      }
    }

  override def getByIdWithPassword(id: UUID): ZIO[ApiContext, Throwable, User] =
    ZIO.fromTry {
      Try {
        val session: Session = driver.session()
        try {
          val query =
            s"""
               |${MatchByIdStatement.apply(id)}
               |${ReturnStatement.apply}
               |""".stripMargin
          val result = session.run(query)
          if (result.hasNext) {
            val record = result.next().get(graph.varName).asMap()
            UserConverter.toAuthDomain(record)
          } else {
            throw NoSuchEntityError(s"${graph.nodeName} with id $id not found")
          }
        } finally {
          session.close()
        }
      }
    }
}
