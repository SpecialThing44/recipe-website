package http.healthcheck

import com.google.inject.{Inject, Singleton}
import play.api.http.ContentTypes
import play.api.mvc.*

import scala.concurrent.Future

@Singleton
class HealthCheckController @Inject() (
    cc: ControllerComponents,
) extends AbstractController(cc) {

  def check(): Action[AnyContent] =
    Action.async {
      Future.successful(
        Ok(
          f"<h3>Hurray! Spencer's recipe website backend is available.</h3>"
        ).as(ContentTypes.HTML)
      )
    }
}
