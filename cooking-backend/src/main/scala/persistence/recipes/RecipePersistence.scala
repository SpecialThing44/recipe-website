package persistence.recipes

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.recipes.Recipe
import org.neo4j.driver.{AuthTokens, Driver, GraphDatabase, Session}
import persistence.neo4j.Database
import play.api.Configuration
import zio.ZIO

import java.util.UUID
import scala.util.Try

class RecipePersistence @Inject() (database: Database) extends Recipes {
  override def list(query: Filters): ZIO[ApiContext, Throwable, Seq[Recipe]] =
    ZIO.succeed(Seq.empty)

  override def create(entity: Recipe): ZIO[ApiContext, Throwable, Recipe] =
    null

  override def update(
      entity: Recipe,
      originalEntity: Recipe
  ): ZIO[ApiContext, Throwable, Recipe] =
    null

  override def delete(id: UUID): ZIO[ApiContext, Throwable, Recipe] =
    null

  override def getById(id: UUID): ZIO[ApiContext, Throwable, Recipe] = null
}
