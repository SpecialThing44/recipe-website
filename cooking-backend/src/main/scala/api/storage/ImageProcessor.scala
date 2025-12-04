package api.storage

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.JpegWriter
import com.sksamuel.scrimage.ScaleMethod
import org.apache.pekko.util.ByteString
import zio.{Task, ZIO}

import javax.imageio.ImageIO
import java.io.ByteArrayInputStream

case class ProcessedImage(
    thumbnail: ByteString,
    medium: ByteString,
    large: ByteString,
    extension: String
)

object ImageProcessor {
  private val THUMBNAIL_SIZE = 150
  private val MEDIUM_SIZE = 500
  private val MAX_SIZE = 2048

  private implicit val writer: JpegWriter = JpegWriter.Default.withCompression(90)
  def processImage(
      imageBytes: ByteString,
      contentType: String
  ): Task[ProcessedImage] = ZIO.attempt {
    if (!contentType.startsWith("image/")) {
      throw new IllegalArgumentException("File must be an image")
    }

    val image = ImmutableImage.loader()
      .fromBytes(imageBytes.toArray)

    val extension = contentType.split("/").lastOption.getOrElse("jpg")

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

    ProcessedImage(thumbnailBytes, mediumBytes, largeBytes, extension)
  }
  
  private def resizeImage(image: ImmutableImage, maxDimension: Int): ImmutableImage = {
    val width = image.width
    val height = image.height

    if (width <= maxDimension && height <= maxDimension) {
      image
    } else {
      image.max(maxDimension, maxDimension, ScaleMethod.Lanczos3)
    }
  }
}
