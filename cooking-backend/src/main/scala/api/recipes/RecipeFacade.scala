package api.recipes

import api.{Persisting, Querying}
import com.google.inject.Inject
import context.ApiContext
import domain.food.recipes.Recipe
import persistence.recipes.Recipes
import play.api.libs.json.JsValue
import zio.ZIO

import java.util.UUID

class RecipeFacade @Inject() (
    val persistence: Recipes
) extends RecipeApi {

  override def create(
      entity: Recipe
  ): ZIO[ApiContext, Throwable, Recipe] = persistence.create(entity)

  override def update(
      entity: Recipe,
      originalEntity: Recipe
  ): ZIO[ApiContext, Throwable, Recipe] = ???

  override def delete(id: UUID): ZIO[ApiContext, Throwable, Recipe] = ???

  override def list(query: JsValue): ZIO[ApiContext, Throwable, Seq[Recipe]] =
    ???

  override def find(query: JsValue): ZIO[ApiContext, Throwable, Recipe] = ???

  override def getById(id: UUID): ZIO[ApiContext, Throwable, Recipe] = ???
}
