package api.ingredients

import api.{Persisting, Querying}
import com.google.inject.ImplementedBy
import context.ApiContext
import domain.ingredients.{Ingredient, IngredientInput, IngredientUpdateInput}
import zio.ZIO

import java.util.UUID

@ImplementedBy(classOf[IngredientsFacade])
trait IngredientsApi
    extends Persisting[Ingredient, IngredientInput, IngredientUpdateInput]
    with Querying[Ingredient] {
  def deleteAll(): ZIO[ApiContext, Throwable, Unit]
  def listSubstitutes(id: UUID): ZIO[ApiContext, Throwable, Seq[Ingredient]]
  def addSubstitute(id: UUID, substituteId: UUID): ZIO[ApiContext, Throwable, Unit]
  def removeSubstitute(id: UUID, substituteId: UUID): ZIO[ApiContext, Throwable, Unit]
}
