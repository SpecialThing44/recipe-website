package api.storage

import com.google.inject.{Inject, Singleton}
import org.apache.pekko.util.ByteString
import play.api.Configuration
import sttp.client3.*
import zio.{Task, ZIO}

import java.util.UUID

@Singleton
class SeaweedFSClient @Inject() (config: Configuration) {
  private val masterUrl = config.get[String]("seaweedfs.masterUrl")
  private val filerUrl = config.get[String]("seaweedfs.filerUrl")
  private val backend = HttpClientSyncBackend()

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

    val fileId = assignResponse.body match {
      case Right(body) =>
        val fidPattern = """"fid":"([^"]+)"""".r
        fidPattern.findFirstMatchIn(body) match {
          case Some(m) => m.group(1)
          case None => throw new Exception("Failed to get file ID from master")
        }
      case Left(error) =>
        throw new Exception(s"Failed to assign file ID: $error")
    }

    val uploadPath = s"/avatars/$userId/$fileName"
    val fullUrl = s"$filerUrl$uploadPath"
    val uploadResponse = basicRequest
      .put(uri"$fullUrl")
      .body(fileBytes.toArray)
      .contentType(contentType)
      .response(asString)
      .send(backend)

    uploadResponse.body match {
      case Right(_)    => fullUrl
      case Left(error) => throw new Exception(s"Failed to upload file: $error")
    }
  }

  def deleteFile(fileUrl: String): Task[Unit] = ZIO.attempt {
    val path = fileUrl.replace(filerUrl, "")
    val fullUrl = s"$filerUrl$path"

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
      fileBytes: ByteString,
      contentType: String,
      userId: UUID
  ): Task[String] = {
    if (!contentType.startsWith("image/")) {
      ZIO.fail(new IllegalArgumentException("File must be an image"))
    } else {
      val extension = contentType.split("/").lastOption.getOrElse("jpg")
      val fileName = s"avatar.$extension"
      uploadFile(fileBytes, contentType, userId, fileName)
    }
  }

  def deleteAvatar(avatarUrl: String): Task[Unit] = {
    deleteFile(avatarUrl)
  }
}
