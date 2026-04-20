package api.recipes

import api.storage.{ImageProcessor, SeaweedFSClient}
import api.users.AuthenticationInteractor
import com.google.inject.Inject
import context.ApiContext
import org.apache.pekko.util.ByteString
import persistence.recipes.Recipes
import zio.ZIO

import java.util.UUID

class RecipeInstructionImageInteractor @Inject() (
    persistence: Recipes,
    seaweedFSClient: SeaweedFSClient
) {

  def uploadInstructionImage(
      recipeId: UUID,
      fileBytes: ByteString,
      contentType: String
  ): ZIO[ApiContext, Throwable, String] = for {
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

    imageId = UUID.randomUUID()
    extension = processedImage.extension
    uploadPath = s"/recipes/$recipeId/instructions/$imageId.$extension"

    imageUrl <- seaweedFSClient.uploadFileToPath(
      processedImage.medium,
      s"image/$extension",
      uploadPath
    )
  } yield imageUrl

  def deleteInstructionImage(
      imageUrl: String
  ): ZIO[ApiContext, Throwable, Unit] = {
    seaweedFSClient.deleteFile(imageUrl).catchAll(_ => ZIO.unit)
  }

  def cleanupOrphanedImages(
      recipeId: UUID,
      oldImages: Seq[String],
      newImages: Seq[String]
  ): ZIO[ApiContext, Throwable, Unit] = {
    val orphanedImages = oldImages.filterNot(newImages.contains)
    ZIO
      .foreach(orphanedImages) { imageUrl =>
        deleteInstructionImage(imageUrl)
      }
      .unit
  }
}
