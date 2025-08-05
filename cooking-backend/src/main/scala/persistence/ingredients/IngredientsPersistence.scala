package persistence.ingredients

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.food.ingredients.Ingredient
import org.neo4j.driver.{AuthTokens, Driver, GraphDatabase}
import play.api.Configuration
import play.api.libs.json.JsValue
import zio.ZIO

import java.util.UUID

class IngredientsPersistence @Inject() (config: Configuration)
    extends Ingredients {

  private val uri = config.get[String]("neo4j.uri")
  private val username = config.get[String]("neo4j.username")
  private val password = config.get[String]("neo4j.password")
  private val driver: Driver =
    GraphDatabase.driver(uri, AuthTokens.basic(username, password))

  override def list(query: Filters): ZIO[ApiContext, Throwable, Seq[Ingredient]] = ???

  override def find(query: Filters): ZIO[ApiContext, Throwable, Ingredient] =
    ???

  override def create(
      entity: Ingredient
  ): ZIO[ApiContext, Throwable, Ingredient] = ???

  override def update(
      entity: Ingredient,
      originalEntity: Ingredient
  ): ZIO[ApiContext, Throwable, Ingredient] =
    ???

  override def delete(id: UUID): ZIO[ApiContext, Throwable, Ingredient] =
    ???
  override def getById(id: UUID): ZIO[ApiContext, Throwable, Ingredient] =
    ???
}
