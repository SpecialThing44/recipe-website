package http.healthcheck

import com.google.inject.{Inject, Singleton}
import play.api.http.ContentTypes
import play.api.mvc._

@Singleton
class HealthCheckController @Inject() (
    cc: ControllerComponents,
) extends AbstractController(cc) {

  def check(): Action[AnyContent] =
    Action {
      Ok(
        f"<h3>Hurray! Spencer's recipe website backend is available.</h3>"
      ).as(ContentTypes.HTML)
    }
}
