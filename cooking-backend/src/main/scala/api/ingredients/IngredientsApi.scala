package api.ingredients

import api.{Persisting, Querying}
import com.google.inject.ImplementedBy
import context.ApiContext
import domain.ingredients.{Ingredient, IngredientInput, IngredientUpdateInput}
import zio.ZIO

@ImplementedBy(classOf[IngredientsFacade])
trait IngredientsApi
    extends Persisting[Ingredient, IngredientInput, IngredientUpdateInput]
    with Querying[Ingredient] {
  def deleteAll(): ZIO[ApiContext, Throwable, Unit]
}
