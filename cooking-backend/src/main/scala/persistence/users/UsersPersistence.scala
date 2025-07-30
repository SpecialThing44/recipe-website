package persistence.users

import com.google.inject.Inject
import context.ApiContext
import domain.people.users.User
import domain.types.NoSuchEntityError
import org.neo4j.driver.{AuthTokens, Driver, GraphDatabase, Session}
import play.api.Configuration
import play.api.libs.json.JsValue
import zio.ZIO

import java.util.UUID
import scala.util.Try

class UsersPersistence @Inject() (config: Configuration) extends Users {

  private val uri = config.get[String]("neo4j.uri")
  private val username = config.get[String]("neo4j.username")
  private val password = config.get[String]("neo4j.password")
  private val driver: Driver =
    GraphDatabase.driver(uri, AuthTokens.basic(username, password))

  override def find(query: JsValue): ZIO[ApiContext, Throwable, User] = ???

  override def list(query: JsValue): ZIO[ApiContext, Throwable, Seq[User]] = ???

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
               |RETURN u
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
               |MATCH (u:User {id: '${entity.idOrError}'})
               |SET $properties
               |RETURN u
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
               |RETURN u
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
               |RETURN u
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
               |RETURN u
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
