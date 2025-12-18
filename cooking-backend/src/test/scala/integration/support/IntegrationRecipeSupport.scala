package integration.support

import api.recipes.RecipeFacade
import context.ApiContext
import domain.filters.Filters
import domain.recipes.{Recipe, RecipeInput, RecipeUpdateInput}
import zio.{Runtime, Unsafe, ZLayer}

import java.util.UUID
import scala.collection.mutable.ListBuffer

trait IntegrationRecipeSupport {
  protected val recipeFacade: RecipeFacade
  protected def createApiContext(): ZLayer[Any, Nothing, ApiContext]
  protected val createdRecipes: ListBuffer[UUID] =
    collection.mutable.ListBuffer.empty[UUID]

  protected def quillDelta(text: String): String = {
    s"""{"ops":[{"insert":"$text\\n"}]}"""
  }

  protected def createTestRecipe(input: RecipeInput): Recipe = {
    val recipe = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          recipeFacade.create(input).provideLayer(createApiContext())
        )
        .getOrThrow()
    }
    createdRecipes += recipe.id
    recipe
  }

  protected def updateRecipe(
      original: Recipe,
      update: RecipeUpdateInput
  ): Recipe = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          recipeFacade.update(update, original).provideLayer(createApiContext())
        )
        .getOrThrow()
    }
  }

  protected def listRecipes(filters: Filters): Seq[Recipe] = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          recipeFacade.list(filters).provideLayer(createApiContext())
        )
        .getOrThrow()
    }
  }

  protected def listSavedRecipes(userId: UUID): Seq[Recipe] = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          recipeFacade
            .list(Filters.empty().copy(savedByUser = Some(userId)))
            .provideLayer(createApiContext())
        )
        .getOrThrow()
    }
  }

  protected def saveRecipe(recipeId: UUID): Recipe = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          recipeFacade.save(recipeId).provideLayer(createApiContext())
        )
        .getOrThrow()
    }
  }

  protected def deleteRecipe(id: UUID): Recipe = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          recipeFacade.delete(id).provideLayer(createApiContext())
        )
        .getOrThrow()
    }
  }

  protected def getRecipeById(id: UUID): Recipe = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          recipeFacade.getById(id).provideLayer(createApiContext())
        )
        .getOrThrow()
    }
  }
}
