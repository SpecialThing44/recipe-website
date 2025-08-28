package api.recipes

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.recipes.Recipe
import persistence.recipes.Recipes
import zio.ZIO

class RecipeFetchInteractor @Inject() (
    persistence: Recipes
) {
  def list(query: Filters): ZIO[ApiContext, Throwable, Seq[Recipe]] =
    persistence.list(query)
}
