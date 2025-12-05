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

  /** Upload an image to be used inline in recipe instructions. Returns the URL
    * of the uploaded image.
    */
  def uploadInstructionImage(
      recipeId: UUID,
      fileBytes: ByteString,
      contentType: String
  ): ZIO[ApiContext, Throwable, String] = for {
    context <- ZIO.service[ApiContext]
    recipe <- persistence.getById(recipeId)

    // Ensure user is authenticated and owns the recipe
    user <- AuthenticationInteractor.ensureIsLoggedIn(
      context.applicationContext.user
    )
    _ <- AuthenticationInteractor.ensureAuthenticatedAndMatchingUser(
      Some(user),
      recipe.createdBy.id
    )

    // Process the image (resize to reasonable size for inline use)
    processedImage <- ImageProcessor.processImage(fileBytes, contentType)

    // Upload medium size for instruction images (balance between quality and file size)
    // Generate unique filename for instruction image
    imageId = UUID.randomUUID()
    extension = processedImage.extension
    uploadPath = s"/recipes/$recipeId/instructions/$imageId.$extension"

    imageUrl <- seaweedFSClient.uploadFileToPath(
      processedImage.medium,
      s"image/$extension",
      uploadPath
    )
  } yield imageUrl

  /** Delete an instruction image. This should be called when cleaning up
    * orphaned images.
    */
  def deleteInstructionImage(
      imageUrl: String
  ): ZIO[ApiContext, Throwable, Unit] = {
    seaweedFSClient.deleteFile(imageUrl).catchAll(_ => ZIO.unit)
  }

  /** Clean up instruction images that are no longer referenced in the recipe.
    * Called when a recipe is updated.
    */
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
