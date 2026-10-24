package api.wiki

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class WikipediaCheckSpec extends AnyFlatSpec with Matchers {
  private val check = new DefaultWikipediaCheck()

  it should "accept HTTPS article URLs on the exact Wikipedia host" in {
    check.wikiLinkIsWellFormed("https://en.wikipedia.org/wiki/Salt") shouldBe true
  }

  it should "reject other destinations and malformed article URLs" in {
    Seq(
      "http://127.0.0.1/health?en.wikipedia.org/wiki/Salt",
      "https://en.wikipedia.org.evil.example/wiki/Salt",
      "https://en.wikipedia.org@evil.example/wiki/Salt",
      "http://en.wikipedia.org/wiki/Salt",
      "https://en.wikipedia.org:8443/wiki/Salt",
      "https://en.wikipedia.org/wiki/",
      "not a URL"
    ).foreach(url => check.wikiLinkIsWellFormed(url) shouldBe false)
  }
}
