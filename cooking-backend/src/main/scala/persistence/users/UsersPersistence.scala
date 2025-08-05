package persistence.users

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.people.users.User
import domain.types.NoSuchEntityError
import org.neo4j.driver.{AuthTokens, Driver, GraphDatabase, Session}
import persistence.cypher.ReturnStatement
import play.api.Configuration
import zio.ZIO

import java.util.UUID
import scala.util.Try

class UsersPersistence @Inject() (config: Configuration) extends Users {

  private val uri = config.get[String]("neo4j.uri")
  private val username = config.get[String]("neo4j.username")
  private val password = config.get[String]("neo4j.password")
  private val driver: Driver =
    GraphDatabase.driver(uri, AuthTokens.basic(username, password))
  private implicit val graph: UserGraph = UserGraph()


  override def find(query: Filters): ZIO[ApiContext, Throwable, User] = ???

  override def list(query: Filters): ZIO[ApiContext, Throwable, Seq[User]] = ???

  override def create(entity: User): ZIO[ApiContext, Throwable, User] =
    ZIO.fromTry {
      Try {
        val session: Session = driver.session()
        try {
          val properties = UserConverter
            .convert(entity)
          val query =
            s"""
               |CREATE (u:User {
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
            .convertForUpdate("u", entity)
          val query =
            s"""
               |MATCH (u:User {id: '${entity.id}'})
               |SET $properties
               |${ReturnStatement.apply}
               |""".stripMargin
          val result = session.run(query)
          println(result.hasNext)
          if (result.hasNext) {
            val record = result.next().get("u").asMap()
            UserConverter.toDomain(record)
          } else {
            throw NoSuchEntityError(
              s"Update for user with id ${entity.id} has failed for some reason"
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
                 |MATCH (u:User {id: '$id'})
                 |DETACH DELETE u
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
               |MATCH (u:User {id: '$id'})
               |${ReturnStatement.apply}
               |""".stripMargin
          val result = session.run(query)
          if (result.hasNext) {
            val record = result.next().get("u").asMap()
            UserConverter.toDomain(record)
          } else {
            throw NoSuchEntityError(s"User with id $id not found")
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
               |MATCH (u:User {email: '$email'})
               |${ReturnStatement.apply}
               |""".stripMargin
          val result = session.run(query)
          if (result.hasNext) {
            val record = result.next().get("u").asMap()
            UserConverter.toAuthDomain(record)
          } else {
            throw NoSuchEntityError(
              s"User with email $email not found"
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
               |MATCH (u:User {id: '$id'})
               |${ReturnStatement.apply}
               |""".stripMargin
          val result = session.run(query)
          if (result.hasNext) {
            val record = result.next().get("u").asMap()
            UserConverter.toAuthDomain(record)
          } else {
            throw NoSuchEntityError(s"User with id $id not found")
          }
        } finally {
          session.close()
        }
      }
    }
}
