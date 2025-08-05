package api.ingredients

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.food.ingredients.Ingredient
import persistence.ingredients.Ingredients
import play.api.libs.json.JsValue
import zio.ZIO

import java.util.UUID

class IngredientsFacade @Inject() (
    val persistence: Ingredients
) extends IngredientsApi {

  override def create(
      entity: Ingredient
  ): ZIO[ApiContext, Throwable, Ingredient] = ???

  override def update(
      entity: Ingredient,
      originalEntity: Ingredient
  ): ZIO[ApiContext, Throwable, Ingredient] = ???

  override def delete(id: UUID): ZIO[ApiContext, Throwable, Ingredient] = ???

  override def list(query: Filters): ZIO[ApiContext, Throwable, Seq[Ingredient]] = ???

  override def find(query: Filters): ZIO[ApiContext, Throwable, Ingredient] =
    ???

  override def getById(id: UUID): ZIO[ApiContext, Throwable, Ingredient] = ???
}
