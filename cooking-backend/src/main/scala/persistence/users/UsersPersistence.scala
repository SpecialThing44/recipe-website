package persistence.users

import com.google.inject.Inject
import context.ApiContext
import domain.people.users.User
import org.neo4j.driver.{AuthTokens, Driver, GraphDatabase, Session}
import play.api.Configuration
import play.api.libs.json.JsValue
import zio.ZIO

import java.time.Instant
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
          val query =
            s"""
               |CREATE (u:User {
               |  id: '${entity.id}',
               |  name: '${entity.name}',
               |  email: '${entity.email}',
               |  saved_recipes: '${Seq.empty}',
               |  country_of_origin: '${entity.countryOfOrigin.getOrElse("")}',
               |  created_on: '${entity.createdOn}',
               |  updated_on: '${entity.updatedOn}'
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
          val query =
            s"""
               |MATCH (u:User {id: '${entity.id}'})
               |SET u.name = '${entity.name}',
               |    u.email = '${entity.email}',
               |    u.saved_recipes = '${Seq.empty}',
               |    u.country_of_origin = '${entity.countryOfOrigin.getOrElse(
                ""
              )}',
               |    u.created_on = '${entity.createdOn}',
               |    u.updated_on = '${entity.updatedOn}'
               |RETURN u
               |""".stripMargin
          session.run(query)
          entity
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
            User(
              id = Some(UUID.fromString(record.get("id").toString)),
              name = record.get("name").toString,
              email = record.get("email").toString,
              savedRecipes = Seq.empty,
              countryOfOrigin =
                Option(record.get("country_of_origin").toString),
              createdOn = Instant.parse(record.get("created_on").toString),
              updatedOn = Instant.parse(record.get("updated_on").toString)
            )
          } else {
            throw new NoSuchElementException(s"User with id $id not found")
          }
        } finally {
          session.close()
        }
      }
    }

  override def authenticate(
      email: String,
      password: String
  ): ZIO[ApiContext, Throwable, Option[User]] = ???
}
