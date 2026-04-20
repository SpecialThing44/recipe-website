package persistence.ingredients

import api.Querying
import com.google.inject.ImplementedBy
import context.ApiContext
import domain.ingredients.Ingredient
import persistence.DbPersisting
import zio.ZIO

import java.util.UUID

@ImplementedBy(classOf[IngredientsPersistence])
trait Ingredients extends DbPersisting[Ingredient] with Querying[Ingredient] {
  def deleteAll(): ZIO[ApiContext, Throwable, Unit]
  def listSubstitutes(id: UUID): ZIO[ApiContext, Throwable, Seq[Ingredient]]
  def addSubstitute(
      id: UUID,
      substituteId: UUID
  ): ZIO[ApiContext, Throwable, Unit]
  def removeSubstitute(
      id: UUID,
      substituteId: UUID
  ): ZIO[ApiContext, Throwable, Unit]
}
