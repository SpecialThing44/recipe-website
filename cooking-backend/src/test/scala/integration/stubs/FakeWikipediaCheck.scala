package integration.stubs

import api.wiki.WikipediaCheck
import zio.ZIO

class FakeWikipediaCheck extends WikipediaCheck {
  override def validateWikiLink(link: String): ZIO[Any, Throwable, Unit] = {
    ZIO.unit
  }

  override def wikiLinkIsWellFormed(link: String): Boolean = {
    true
  }
}
