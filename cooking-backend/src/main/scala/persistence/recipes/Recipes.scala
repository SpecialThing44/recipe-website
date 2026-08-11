package persistence.recipes

import api.Querying
import com.google.inject.ImplementedBy
import domain.recipes.Recipe
import persistence.DbPersisting
import zio.ZIO

import java.util.UUID

@ImplementedBy(classOf[RecipePersistence])
trait Recipes extends DbPersisting[Recipe] with Querying[Recipe] {
  def save(
      recipeId: java.util.UUID,
      userId: java.util.UUID
  ): zio.ZIO[context.ApiContext, Throwable, Recipe]
  def deleteAll(): zio.ZIO[context.ApiContext, Throwable, Unit]
  def wouldCreateCycle(
      recipeId: UUID,
      componentRecipeIds: Seq[UUID]
  ): ZIO[context.ApiContext, Throwable, Boolean]
}
