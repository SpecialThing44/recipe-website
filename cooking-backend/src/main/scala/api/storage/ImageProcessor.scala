package api.storage

import com.sksamuel.scrimage.webp.WebpWriter
import com.sksamuel.scrimage.{ImmutableImage, ScaleMethod}
import org.apache.pekko.util.ByteString
import zio.{Task, ZIO}
import domain.types.InputError
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.webp.WebpDirectory

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
  private val processingSlots = new java.util.concurrent.Semaphore(2)
  private val maximumPixels = 16000000L
  private val maximumUploadBytes = 5 * 1024 * 1024
  private val OUTPUT_EXTENSION = "webp"

  private implicit val writer: WebpWriter =
    WebpWriter.DEFAULT.withQ(95)
  def processImage(
      imageBytes: ByteString,
      contentType: String
  ): Task[ProcessedImage] = ZIO.acquireReleaseWith(
    ZIO.attemptBlockingInterrupt(processingSlots.acquire())
  )(_ => ZIO.succeed(processingSlots.release())) { _ =>
    ZIO.attemptBlocking {
      if (!contentType.startsWith("image/")) {
        throw InputError("File must be an image")
      }

      if (imageBytes.isEmpty || imageBytes.length > maximumUploadBytes)
        throw InputError("Image must contain between 1 byte and 5 MB")
      val bytes = imageBytes.toArray
      validateDimensions(bytes)
      val image = ImmutableImage
        .loader()
        .fromBytes(bytes)

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
  }

  private def validateDimensions(bytes: Array[Byte]): Unit = {
    val stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))
    try {
      val readers = ImageIO.getImageReaders(stream)
      val (width, height) = if (readers.hasNext) {
        val reader = readers.next()
        try {
          reader.setInput(stream)
          (reader.getWidth(0), reader.getHeight(0))
        } finally reader.dispose()
      } else {
        val metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes))
        val webp = metadata.getFirstDirectoryOfType(classOf[WebpDirectory])
        if (webp == null) throw InputError("Unsupported image format")
        (webp.getInt(WebpDirectory.TAG_IMAGE_WIDTH), webp.getInt(WebpDirectory.TAG_IMAGE_HEIGHT))
      }
      if (width <= 0 || height <= 0 || width.toLong * height > maximumPixels)
        throw InputError("Image must contain at most 16 million pixels")
    } finally stream.close()
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
