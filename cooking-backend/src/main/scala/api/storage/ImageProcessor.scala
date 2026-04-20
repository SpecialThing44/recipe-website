package api.storage

import com.sksamuel.scrimage.webp.WebpWriter
import com.sksamuel.scrimage.{ImmutableImage, ScaleMethod}
import org.apache.pekko.util.ByteString
import zio.{Task, ZIO}

case class ProcessedImage(
    thumbnail: ByteString,
    medium: ByteString,
    large: ByteString,
    extension: String
)

object ImageProcessor {
  private val THUMBNAIL_SIZE = 300
  private val MEDIUM_SIZE = 500
  private val MAX_SIZE = 2048
  private val OUTPUT_EXTENSION = "webp"

  private implicit val writer: WebpWriter =
    WebpWriter.DEFAULT.withQ(95)
  def processImage(
      imageBytes: ByteString,
      contentType: String
  ): Task[ProcessedImage] = ZIO.attempt {
    if (!contentType.startsWith("image/")) {
      throw new IllegalArgumentException("File must be an image")
    }

    val image = ImmutableImage
      .loader()
      .fromBytes(imageBytes.toArray)

    val thumbnail = resizeImage(image, THUMBNAIL_SIZE)

    val medium = resizeImage(image, MEDIUM_SIZE)

    val large = if (image.width > MAX_SIZE || image.height > MAX_SIZE) {
      resizeImage(image, MAX_SIZE)
    } else {
      image
    }

    val thumbnailBytes = ByteString(thumbnail.bytes(writer))
    val mediumBytes = ByteString(medium.bytes(writer))
    val largeBytes = ByteString(large.bytes(writer))

    ProcessedImage(
      thumbnailBytes,
      mediumBytes,
      largeBytes,
      OUTPUT_EXTENSION
    )
  }

  private def resizeImage(
      image: ImmutableImage,
      maxDimension: Int
  ): ImmutableImage = {
    val width = image.width
    val height = image.height

    if (width <= maxDimension && height <= maxDimension) {
      image
    } else {
      image.max(maxDimension, maxDimension, ScaleMethod.Lanczos3)
    }
  }
}
