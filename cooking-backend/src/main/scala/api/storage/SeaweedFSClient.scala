package api.storage

import com.google.inject.{Inject, Singleton}
import org.apache.pekko.util.ByteString
import play.api.Configuration
import sttp.client3.*
import zio.{Task, ZIO}
import java.util.UUID

case class StorageAvatarUrls(
    thumbnailUrl: String,
    mediumUrl: String,
    largeUrl: String
)

@Singleton
class SeaweedFSClient @Inject() (config: Configuration) {
  private val masterUrl = config.get[String]("seaweedfs.masterUrl")
  private val internalFilerUrl = config
    .get[String]("seaweedfs.internalFilerUrl")
    .stripSuffix("/")
  private val publicFilerUrl = config
    .get[String]("seaweedfs.publicFilerUrl")
    .stripSuffix("/")
  private val backend = HttpClientSyncBackend()

  private def urlFor(baseUrl: String, path: String): String =
    s"$baseUrl/${path.stripPrefix("/")}"

  private def internalUrlFor(path: String): String =
    urlFor(internalFilerUrl, path)

  private def publicUrlFor(path: String): String =
    urlFor(publicFilerUrl, path)

  private def pathFromUrl(fileUrl: String): String = {
    require(
      fileUrl.startsWith(publicFilerUrl) || fileUrl.startsWith(
        internalFilerUrl
      ) || !fileUrl.contains("://"),
      s"Unexpected filer url: $fileUrl"
    )
    fileUrl.stripPrefix(publicFilerUrl).stripPrefix(internalFilerUrl)
  }

  def uploadFile(
      fileBytes: ByteString,
      contentType: String,
      userId: UUID,
      fileName: String
  ): Task[String] = ZIO.attempt {
    val assignResponse = basicRequest
      .get(uri"$masterUrl/dir/assign")
      .response(asString)
      .send(backend)

    assignResponse.body match {
      case Right(_) =>
        val uploadPath = s"/avatars/$userId/$fileName"
        val fullUrl = internalUrlFor(uploadPath)
        val uploadResponse = basicRequest
          .put(uri"$fullUrl")
          .body(fileBytes.toArray)
          .contentType(contentType)
          .response(asString)
          .send(backend)

        uploadResponse.body match {
          case Right(_) => publicUrlFor(uploadPath)
          case Left(error) =>
            throw new Exception(s"Failed to upload file: $error")
        }
      case Left(error) =>
        throw new Exception(s"Failed to assign file ID: $error")
    }
  }

  def deleteFile(fileUrl: String): Task[Unit] = ZIO.attempt {
    val fullUrl = internalUrlFor(pathFromUrl(fileUrl))

    val deleteResponse = basicRequest
      .delete(uri"$fullUrl")
      .response(asString)
      .send(backend)

    deleteResponse.body match {
      case Right(_)    => ()
      case Left(error) => throw new Exception(s"Failed to delete file: $error")
    }
  }

  def uploadAvatar(
      processedImage: ProcessedImage,
      userId: UUID
  ): Task[StorageAvatarUrls] = {
    val extension = processedImage.extension

    for {
      thumbnailUrl <- uploadFile(
        processedImage.thumbnail,
        s"image/$extension",
        userId,
        s"avatar-thumbnail.$extension"
      )
      mediumUrl <- uploadFile(
        processedImage.medium,
        s"image/$extension",
        userId,
        s"avatar-medium.$extension"
      )
      largeUrl <- uploadFile(
        processedImage.large,
        s"image/$extension",
        userId,
        s"avatar.$extension"
      )
    } yield StorageAvatarUrls(thumbnailUrl, mediumUrl, largeUrl)
  }

  def deleteAllAvatarSizes(
      userId: UUID,
      extension: String = "jpg"
  ): Task[Unit] = {
    for {
      _ <- deleteFile(
        publicUrlFor(s"/avatars/$userId/avatar-thumbnail.$extension")
      )
        .catchAll(_ => ZIO.unit)
      _ <- deleteFile(
        publicUrlFor(s"/avatars/$userId/avatar-medium.$extension")
      )
        .catchAll(_ => ZIO.unit)
      _ <- deleteFile(publicUrlFor(s"/avatars/$userId/avatar.$extension"))
        .catchAll(_ => ZIO.unit)
    } yield ()
  }

  def uploadRecipeImage(
      processedImage: ProcessedImage,
      recipeId: UUID
  ): Task[StorageAvatarUrls] = {
    val extension = processedImage.extension

    for {
      thumbnailUrl <- uploadFileToPath(
        processedImage.thumbnail,
        s"image/$extension",
        s"/recipes/$recipeId/recipe-thumbnail.$extension"
      )
      mediumUrl <- uploadFileToPath(
        processedImage.medium,
        s"image/$extension",
        s"/recipes/$recipeId/recipe-medium.$extension"
      )
      largeUrl <- uploadFileToPath(
        processedImage.large,
        s"image/$extension",
        s"/recipes/$recipeId/recipe.$extension"
      )
    } yield StorageAvatarUrls(thumbnailUrl, mediumUrl, largeUrl)
  }

  def uploadFileToPath(
      fileBytes: ByteString,
      contentType: String,
      uploadPath: String
  ): Task[String] = ZIO.attempt {
    val assignResponse = basicRequest
      .get(uri"$masterUrl/dir/assign")
      .response(asString)
      .send(backend)

    assignResponse.body match {
      case Right(_) =>
        val fullUrl = internalUrlFor(uploadPath)
        val uploadResponse = basicRequest
          .put(uri"$fullUrl")
          .body(fileBytes.toArray)
          .contentType(contentType)
          .response(asString)
          .send(backend)

        uploadResponse.body match {
          case Right(_) => publicUrlFor(uploadPath)
          case Left(error) =>
            throw new Exception(s"Failed to upload file: $error")
        }
      case Left(error) =>
        throw new Exception(s"Failed to assign file ID: $error")
    }
  }

  def deleteAllImageSizes(
      recipeId: UUID,
      extension: String = "jpg"
  ): Task[Unit] = {
    for {
      _ <- deleteFile(
        publicUrlFor(s"/recipes/$recipeId/recipe-thumbnail.$extension")
      )
        .catchAll(_ => ZIO.unit)
      _ <- deleteFile(
        publicUrlFor(s"/recipes/$recipeId/recipe-medium.$extension")
      )
        .catchAll(_ => ZIO.unit)
      _ <- deleteFile(publicUrlFor(s"/recipes/$recipeId/recipe.$extension"))
        .catchAll(_ => ZIO.unit)
    } yield ()
  }
}
