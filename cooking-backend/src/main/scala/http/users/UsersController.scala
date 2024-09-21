package http.users

import com.google.inject.{Inject, Singleton}
import play.api.mvc._

@Singleton
class UsersController @Inject() (cc: ControllerComponents)
    extends AbstractController(cc) {
  def get(id: java.util.UUID): Action[AnyContent] =
    Action {
      Ok("" + id.toString)
    }

  def put(id: java.util.UUID): Action[AnyContent] = Action { request =>
    val formData = request.body
    // Process the form data
    Created(s"ID: $id, Form Data: $formData")
  }
}
