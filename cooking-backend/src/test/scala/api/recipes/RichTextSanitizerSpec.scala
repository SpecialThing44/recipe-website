package api.recipes

import api.moderation.OpenAIModerationClient
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import zio.{Runtime, Unsafe}

class RichTextSanitizerSpec extends AnyFlatSpec with Matchers {
  private val sanitizer = new RichTextSanitizer(new OpenAIModerationClient(Configuration.from(Map(
    "openai.apiKey" -> "", "openai.skipModeration" -> true
  ))))
  private def accepts(instructions: String): Boolean = Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(sanitizer.validateAndSanitize(instructions).either).getOrThrowFiberFailure().isRight
  }

  it should "validate image URLs in the actual Quill embed location" in {
    accepts("""{"ops":[{"insert":{"image":"https://images.example/step.webp"}}]}""") shouldBe true
    Seq("javascript:alert(1)", "data:image/png;base64,abc", "//other.example/image", "https://user@other.example/image").foreach { url =>
      accepts(s"""{"ops":[{"insert":{"image":"$url"}}]}""") shouldBe false
    }
  }

  it should "reject malformed inserts and oversized input" in {
    Seq("""{"ops":[{"insert":3}]}""", """{"ops":[{"insert":{"video":"https://example.com"}}]}""",
      """{"ops":[{"insert":"Text","attributes":3}]}""", "x" * 50001).foreach { instructions =>
      accepts(instructions) shouldBe false
    }
  }
}
