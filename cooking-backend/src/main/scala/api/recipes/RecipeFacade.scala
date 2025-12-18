package api.recipes

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.recipes.{Recipe, RecipeInput, RecipeUpdateInput}
import persistence.recipes.Recipes
import zio.ZIO

import java.util.UUID

class RecipeFacade @Inject() (
    persistence: Recipes,
    createInteractor: RecipeCreateInteractor,
    updateInteractor: RecipeUpdateInteractor,
    deleteInteractor: RecipeDeleteInteractor,
    fetchInteractor: RecipeFetchInteractor,
    saveInteractor: RecipeSaveInteractor,
    getInteractor: RecipeGetInteractor,
    imageInteractor: RecipeImageInteractor,
    instructionImageInteractor: RecipeInstructionImageInteractor,
) extends RecipeApi {

  override def create(
      entity: RecipeInput
  ): ZIO[ApiContext, Throwable, Recipe] =
    createInteractor.create(entity)

  override def update(
      entity: RecipeUpdateInput,
      originalEntity: Recipe
  ): ZIO[ApiContext, Throwable, Recipe] =
    updateInteractor.update(entity, originalEntity)

  override def delete(id: UUID): ZIO[ApiContext, Throwable, Recipe] =
    deleteInteractor.delete(id)

  override def list(query: Filters): ZIO[ApiContext, Throwable, Seq[Recipe]] =
    fetchInteractor.list(query)

  override def getById(id: UUID): ZIO[ApiContext, Throwable, Recipe] =
    getInteractor.getById(id)

  override def save(recipeId: UUID): ZIO[ApiContext, Throwable, Recipe] =
    saveInteractor.save(recipeId)

  override def deleteAll(): ZIO[ApiContext, Throwable, Unit] =
    persistence.deleteAll()

  override def uploadImage(
      recipeId: UUID,
      fileBytes: org.apache.pekko.util.ByteString,
      contentType: String
  ): ZIO[ApiContext, Throwable, Recipe] =
    imageInteractor.uploadImage(recipeId, fileBytes, contentType)

  override def deleteImage(recipeId: UUID): ZIO[ApiContext, Throwable, Recipe] =
    imageInteractor.deleteImage(recipeId)

  override def uploadInstructionImage(
      recipeId: UUID,
      fileBytes: org.apache.pekko.util.ByteString,
      contentType: String
  ): ZIO[ApiContext, Throwable, String] =
    instructionImageInteractor.uploadInstructionImage(
      recipeId,
      fileBytes,
      contentType
    )
}
