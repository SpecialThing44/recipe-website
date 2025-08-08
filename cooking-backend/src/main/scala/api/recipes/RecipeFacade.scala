package api.recipes

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.food.recipes.{Recipe, RecipeInput}
import persistence.recipes.Recipes
import play.api.libs.json.JsValue
import zio.ZIO

import java.util.UUID

class RecipeFacade @Inject() (
    val persistence: Recipes
) extends RecipeApi {

  override def create(
      entity: RecipeInput
  ): ZIO[ApiContext, Throwable, Recipe] =
    persistence.create(RecipeAdapter.adapt(entity))

  override def update(
      entity: RecipeInput,
      originalEntity: Recipe
  ): ZIO[ApiContext, Throwable, Recipe] =
    persistence.update(RecipeAdapter.adapt(entity), originalEntity)

  override def delete(id: UUID): ZIO[ApiContext, Throwable, Recipe] = ???

  override def list(query: Filters): ZIO[ApiContext, Throwable, Seq[Recipe]] =
    ???

  override def getById(id: UUID): ZIO[ApiContext, Throwable, Recipe] = ???
}
