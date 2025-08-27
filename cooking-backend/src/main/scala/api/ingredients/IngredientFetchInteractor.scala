package api.ingredients

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.ingredients.Ingredient
import persistence.ingredients.Ingredients
import zio.ZIO

class IngredientFetchInteractor @Inject() (
    persistence: Ingredients,
) {
  def list(query: Filters): ZIO[ApiContext, Throwable, Seq[Ingredient]] =
    persistence.list(query)
}
