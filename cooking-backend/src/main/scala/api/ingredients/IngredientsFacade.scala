package api.ingredients

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.ingredients.{Ingredient, IngredientInput, IngredientUpdateInput}
import persistence.ingredients.Ingredients
import zio.ZIO

import java.util.UUID

class IngredientsFacade @Inject() (
    persistence: Ingredients,
    createInteractor: IngredientCreateInteractor,
    updateInteractor: IngredientUpdateInteractor,
    deleteInteractor: IngredientDeleteInteractor,
    fetchInteractor: IngredientFetchInteractor,
) extends IngredientsApi {

  override def create(
      entity: IngredientInput
  ): ZIO[ApiContext, Throwable, Ingredient] = {
    createInteractor.create(entity)
  }

  override def update(
      entity: IngredientUpdateInput,
      originalEntity: Ingredient
  ): ZIO[ApiContext, Throwable, Ingredient] = {
    updateInteractor.update(entity, originalEntity)
  }

  override def delete(id: UUID): ZIO[ApiContext, Throwable, Ingredient] = {
    deleteInteractor.delete(id)
  }

  override def list(
      query: Filters
  ): ZIO[ApiContext, Throwable, Seq[Ingredient]] = {
    fetchInteractor.list(query)
  }

  override def getById(id: UUID): ZIO[ApiContext, Throwable, Ingredient] = {
    persistence.getById(id)
  }
}
