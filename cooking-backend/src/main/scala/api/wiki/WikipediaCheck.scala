package api.wiki

import com.google.inject.{ImplementedBy, Singleton}
import domain.types.InputError
import sttp.client3.{
  HttpURLConnectionBackend,
  Identity,
  SttpBackend,
  UriContext,
  basicRequest
}
import sttp.model.StatusCode
import zio.ZIO

@ImplementedBy(classOf[DefaultWikipediaCheck])
trait WikipediaCheck {
  def validateWikiLink(link: String): ZIO[Any, Throwable, Unit]
  def wikiLinkIsWellFormed(link: String): Boolean
}

@Singleton
class DefaultWikipediaCheck extends WikipediaCheck {
  private val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

  override def validateWikiLink(link: String): ZIO[Any, Throwable, Unit] = {
    val processedLink = processWikiLink(link)
    val wellFormedLink = wikiLinkIsWellFormed(processedLink)
    val realLink = wikiLinkIsReal(processedLink)
    (wellFormedLink, realLink) match {
      case (false, _) => ZIO.fail(InputError("Link is not well formed"))
      case (_, false) =>
        ZIO.fail(InputError("Link does not reach a real wiki page"))
      case _ => ZIO.unit
    }
  }

  private def processWikiLink(link: String): String = link.trim

  override def wikiLinkIsWellFormed(link: String): Boolean =
    link.contains("en.wikipedia.org/wiki/")

  private def wikiLinkIsReal(link: String): Boolean = {
    val response = basicRequest.head(uri"$link").send(backend)
    response.code match {
      case StatusCode.Ok => true
      case _             => false
    }
  }
}

object WikipediaCheck {
  def validateWikiLink(link: String): ZIO[Any, Throwable, Unit] =
    new DefaultWikipediaCheck().validateWikiLink(link)

  def wikiLinkIsWellFormed(link: String): Boolean =
    new DefaultWikipediaCheck().wikiLinkIsWellFormed(link)
}
