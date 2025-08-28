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
  def list(query: Filters): ZIO[ApiContext, Throwable, Seq[Recipe]] = {
    for {
      context <- ZIO.service[ApiContext]
      userOpt = context.applicationContext.user
      base <- persistence.list(query)
      filtered = base.filter { r =>
        r.public || userOpt.exists(u => u.id == r.createdBy.id)
      }
    } yield filtered
  }
}
