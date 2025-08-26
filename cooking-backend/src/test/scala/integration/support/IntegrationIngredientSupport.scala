package integration.support

import api.ingredients.IngredientsFacade
import context.ApiContext
import domain.filters.Filters
import domain.ingredients.{Ingredient, IngredientInput}
import zio.{Runtime, Unsafe, ZLayer}

import java.util.UUID
import scala.collection.mutable.ListBuffer

trait IntegrationIngredientSupport {
  protected val ingredientsFacade: IngredientsFacade
  protected val createdIngredients: ListBuffer[UUID] = collection.mutable.ListBuffer.empty[UUID]
  protected def createApiContext(): ZLayer[Any, Nothing, ApiContext]
  protected def createTestIngredient(ingredientInput: IngredientInput): Ingredient = {
    val ingredient = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          ingredientsFacade.create(ingredientInput).provideLayer(createApiContext())
        )
        .getOrThrow()
    }

    createdIngredients += ingredient.id
    ingredient
  }

  protected def updateIngredient(ingredient: Ingredient, updateInput: domain.ingredients.IngredientUpdateInput): Ingredient = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          ingredientsFacade.update(updateInput, ingredient).provideLayer(createApiContext())
        )
        .getOrThrow()
    }
  }

  protected def listIngredients(filters: Filters): Seq[Ingredient] = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          ingredientsFacade.list(filters).provideLayer(createApiContext())
        )
        .getOrThrow()
    }
  }

  protected def deleteIngredient(ingredientId: UUID): Ingredient = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          ingredientsFacade.delete(ingredientId).provideLayer(createApiContext())
        )
        .getOrThrow()
    }
  }

  protected def getIngredientById(ingredientId: UUID): Ingredient = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          ingredientsFacade.getById(ingredientId).provideLayer(createApiContext())
        )
        .getOrThrow()
    }
  }
}
