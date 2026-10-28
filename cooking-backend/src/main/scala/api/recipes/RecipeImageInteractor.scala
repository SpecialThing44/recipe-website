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

    processedImage <- ImageProcessor.processImage(fileBytes, contentType)
    seaweedUrls <- seaweedFSClient.uploadRecipeImage(processedImage, recipeId)

    imageUrls = domain.users.AvatarUrls(
      thumbnail = seaweedUrls.thumbnailUrl,
      medium = seaweedUrls.mediumUrl,
      large = seaweedUrls.largeUrl
    )

    updateInput = RecipeUpdateInput(image = Some(imageUrls))
    updatedRecipe <- updateInteractor
      .update(updateInput, recipe)
      .onError(_ =>
        seaweedFSClient.deleteImageUrls(imageUrls, s"/recipes/$recipeId").ignore
      )
    _ <- ZIO.foreachDiscard(recipe.image)(urls =>
      seaweedFSClient.deleteImageUrls(urls, s"/recipes/$recipeId")
    )
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

    updateInput = RecipeUpdateInput(removeImage = true)
    updatedRecipe <- updateInteractor.update(updateInput, recipe)
    _ <- ZIO.foreachDiscard(recipe.image)(urls =>
      seaweedFSClient.deleteImageUrls(urls, s"/recipes/$recipeId")
    )
  } yield updatedRecipe
}
