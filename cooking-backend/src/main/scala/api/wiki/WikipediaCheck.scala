package api.wiki

import com.google.inject.{ImplementedBy, Singleton}
import domain.types.InputError
import sttp.client4.{SyncBackend, UriContext, basicRequest}
import sttp.client4.httpurlconnection.HttpURLConnectionBackend
import sttp.model.StatusCode
import scala.concurrent.duration.*
import zio.ZIO
import java.net.URI
import scala.util.Try

@ImplementedBy(classOf[DefaultWikipediaCheck])
trait WikipediaCheck {
  def validateWikiLink(link: String): ZIO[Any, Throwable, Unit]
  def wikiLinkIsWellFormed(link: String): Boolean
}

@Singleton
class DefaultWikipediaCheck extends WikipediaCheck {
  private val backend: SyncBackend = HttpURLConnectionBackend()

  override def validateWikiLink(link: String): ZIO[Any, Throwable, Unit] = {
    val processedLink = processWikiLink(link)
    val wellFormedLink = wikiLinkIsWellFormed(processedLink)

    if (!wellFormedLink) {
      ZIO.fail(InputError("Link is not well formed"))
    } else {
      ZIO
        .attemptBlocking {
          wikiLinkIsReal(processedLink)
        }
        .flatMap {
          case true  => ZIO.unit
          case false =>
            ZIO.fail(InputError("Link does not reach a real wiki page"))
        }
    }
  }

  private def processWikiLink(link: String): String = link.trim

  override def wikiLinkIsWellFormed(link: String): Boolean =
    Try(new URI(link.trim)).toOption.exists(uri =>
      uri.getScheme == "https" &&
        uri.getHost == "en.wikipedia.org" &&
        uri.getUserInfo == null &&
        (uri.getPort == -1 || uri.getPort == 443) &&
        Option(uri.getPath).exists(path =>
          path.startsWith("/wiki/") && path.length > 6
        )
    )

  private def wikiLinkIsReal(link: String): Boolean = {
    val response = basicRequest
      .head(uri"$link")
      .header(
        "User-Agent",
        "RecipeWebsite/1.0 (wiki validation for new ingredients)"
      )
      .followRedirects(false)
      .readTimeout(10.seconds)
      .send(backend)
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
