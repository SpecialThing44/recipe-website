package api.wiki

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

object WikipediaCheck {
  val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

  def validateWikiLink(link: String): ZIO[Any, Throwable, Unit] = {
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

  private def processWikiLink(link: String): String = {
    val trimmedLink = link.trim
    trimmedLink match {
      case l if l.startsWith("https://") => l.substring("https://".length)
      case l if l.startsWith("http://")  => l.substring("http://".length)
      case l if l.startsWith("www.")     => l.substring("www.".length)
      case _                             => trimmedLink
    }

  }

  def wikiLinkIsWellFormed(link: String): Boolean =
    link.startsWith("en.wikipedia.org/wiki/")

  private def wikiLinkIsReal(link: String): Boolean = {
    val response = basicRequest.head(uri"$link").send(backend)
    response.code match {
      case StatusCode.Ok => true
      case _             => false
    }
  }
}
