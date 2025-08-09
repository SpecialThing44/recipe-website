package persistence.recipes

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.recipes.Recipe
import org.neo4j.driver.{AuthTokens, Driver, GraphDatabase, Session}
import play.api.Configuration
import zio.ZIO

import java.util.UUID
import scala.util.Try

class RecipePersistence @Inject() (config: Configuration) extends Recipes {

  private val uri = config.get[String]("neo4j.uri")
  private val username = config.get[String]("neo4j.username")
  private val password = config.get[String]("neo4j.password")
  private val driver: Driver =
    GraphDatabase.driver(uri, AuthTokens.basic(username, password))

  override def list(query: Filters): ZIO[ApiContext, Throwable, Seq[Recipe]] =
    ???

  override def create(entity: Recipe): ZIO[ApiContext, Throwable, Recipe] =
    ZIO.fromTry {
      Try {
        val session: Session = driver.session()
        try {
          val query =
            s"""""".stripMargin
          session.run(query)
          entity
        } finally {
          session.close()
        }
      }
    }

  override def update(
      entity: Recipe,
      originalEntity: Recipe
  ): ZIO[ApiContext, Throwable, Recipe] =
    ZIO.fromTry {
      Try {
        val session: Session = driver.session()
        try {
          val query =
            s"""""".stripMargin
          session.run(query)
          entity
        } finally {
          session.close()
        }
      }
    }

  override def delete(id: UUID): ZIO[ApiContext, Throwable, Recipe] =
    for {
      recipe <- getById(id)
      _ <- ZIO.fromTry {
        Try {
          val session: Session = driver.session()
          try {
            val query =
              s"""""".stripMargin
            session.run(query)
          } finally {
            session.close()
          }
        }
      }
    } yield recipe

  override def getById(id: UUID): ZIO[ApiContext, Throwable, Recipe] = ???
}
