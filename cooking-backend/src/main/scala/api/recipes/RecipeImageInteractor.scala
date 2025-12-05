package api.recipes

import api.storage.{ImageProcessor, SeaweedFSClient}
import api.users.AuthenticationInteractor
import com.google.inject.Inject
import context.ApiContext
import domain.recipes.{Recipe, RecipeUpdateInput}
import org.apache.pekko.util.ByteString
import persistence.recipes.Recipes
import zio.ZIO

import java.util.UUID

class RecipeImageInteractor @Inject() (
    persistence: Recipes,
    seaweedFSClient: SeaweedFSClient,
    updateInteractor: RecipeUpdateInteractor
) {

  def uploadImage(
      recipeId: UUID,
      fileBytes: ByteString,
      contentType: String
  ): ZIO[ApiContext, Throwable, Recipe] = for {
    context <- ZIO.service[ApiContext]
    recipe <- persistence.getById(recipeId)

    user <- AuthenticationInteractor.ensureIsLoggedIn(
      context.applicationContext.user
    )
    _ <- AuthenticationInteractor.ensureAuthenticatedAndMatchingUser(
      Some(user),
      recipe.createdBy.id
    )

    _ <- recipe.image match {
      case Some(image) =>
        val extension = image.large.split("\\.").lastOption.getOrElse("jpg")
        seaweedFSClient
          .deleteAllImageSizes(recipeId, extension)
          .catchAll(_ => ZIO.unit)
      case None => ZIO.unit
    }

    processedImage <- ImageProcessor.processImage(fileBytes, contentType)
    seaweedUrls <- seaweedFSClient.uploadRecipeImage(processedImage, recipeId)

    imageUrls = domain.users.AvatarUrls(
      thumbnail = seaweedUrls.thumbnailUrl,
      medium = seaweedUrls.mediumUrl,
      large = seaweedUrls.largeUrl
    )

    updateInput = RecipeUpdateInput(image = Some(imageUrls))
    updatedRecipe <- updateInteractor.update(updateInput, recipe)
  } yield updatedRecipe

  def deleteImage(recipeId: UUID): ZIO[ApiContext, Throwable, Recipe] = for {
    context <- ZIO.service[ApiContext]
    recipe <- persistence.getById(recipeId)

    user <- AuthenticationInteractor.ensureIsLoggedIn(
      context.applicationContext.user
    )
    _ <- AuthenticationInteractor.ensureAuthenticatedAndMatchingUser(
      Some(user),
      recipe.createdBy.id
    )

    _ <- recipe.image match {
      case Some(image) =>
        val extension = image.large.split("\\.").lastOption.getOrElse("jpg")
        seaweedFSClient.deleteAllImageSizes(recipeId, extension)
      case None => ZIO.unit
    }

    updateInput = RecipeUpdateInput(image = None)
    updatedRecipe <- updateInteractor.update(updateInput, recipe)
  } yield updatedRecipe
}
