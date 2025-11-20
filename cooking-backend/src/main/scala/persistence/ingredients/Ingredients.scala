package persistence.ingredients

import api.Querying
import com.google.inject.ImplementedBy
import context.ApiContext
import domain.ingredients.Ingredient
import persistence.DbPersisting
import zio.ZIO

@ImplementedBy(classOf[IngredientsPersistence])
trait Ingredients extends DbPersisting[Ingredient] with Querying[Ingredient] {
  def deleteAll(): ZIO[ApiContext, Throwable, Unit]
}
