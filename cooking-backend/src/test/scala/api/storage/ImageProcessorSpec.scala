package api.storage

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import javax.imageio.ImageIO
import org.apache.pekko.util.ByteString
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import zio.{Runtime, Unsafe}

class ImageProcessorSpec extends AnyFlatSpec with Matchers {
  private def png: Array[Byte] = {
    val output = new ByteArrayOutputStream()
    ImageIO.write(
      new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB),
      "png",
      output
    )
    output.toByteArray
  }

  it should "reject oversized image dimensions before decoding pixels" in {
    val bytes = png
    ByteBuffer.wrap(bytes).putInt(16, 100000).putInt(20, 100000)
    val checksum = new CRC32()
    checksum.update(bytes, 12, 17)
    ByteBuffer.wrap(bytes).putInt(29, checksum.getValue.toInt)
    val result = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(ImageProcessor.processImage(ByteString(bytes), "image/png").either)
        .getOrThrowFiberFailure()
    }
    result.left.toOption.get.getMessage should include("16 million pixels")
  }

  it should "process ordinary images into WebP sizes" in {
    val image = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(ImageProcessor.processImage(ByteString(png), "image/png"))
        .getOrThrowFiberFailure()
    }
    image.extension shouldBe "webp"
    image.thumbnail.nonEmpty shouldBe true
    image.medium.nonEmpty shouldBe true
    image.large.nonEmpty shouldBe true
    val reprocessed = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(ImageProcessor.processImage(image.large, "image/webp"))
        .getOrThrowFiberFailure()
    }
    reprocessed.large.nonEmpty shouldBe true
  }
}
