package persistence.recipes

import api.Querying
import com.google.inject.ImplementedBy
import domain.recipes.Recipe
import persistence.DbPersisting

@ImplementedBy(classOf[RecipePersistence])
trait Recipes extends DbPersisting[Recipe] with Querying[Recipe] {
  def save(
      recipeId: java.util.UUID,
      userId: java.util.UUID
  ): zio.ZIO[context.ApiContext, Throwable, Recipe]
  def deleteAll(): zio.ZIO[context.ApiContext, Throwable, Unit]
}
