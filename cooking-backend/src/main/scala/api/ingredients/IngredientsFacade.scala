package api.ingredients

import com.google.inject.Inject
import context.ApiContext
import domain.food.ingredients.Ingredient
import persistence.recipes.Ingredients
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

  override def list(
      query: JsValue
  ): ZIO[ApiContext, Throwable, Seq[Ingredient]] = ???

  override def find(query: JsValue): ZIO[ApiContext, Throwable, Ingredient] =
    ???

  override def get(id: UUID): ZIO[ApiContext, Throwable, Ingredient] = ???
}
