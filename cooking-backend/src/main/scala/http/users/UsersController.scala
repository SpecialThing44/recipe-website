package http.users

import com.google.inject.{Inject, Singleton}
import context.CookingApi
import domain.people.users.User
import http.Requests
import io.circe.Decoder
import play.api.libs.json.JsValue
import play.api.mvc.*

@Singleton
class UsersController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi
) extends AbstractController(cc) {
  implicit val recipeDecoder: Decoder[User] = User.decoder
  def get(id: java.util.UUID): Action[AnyContent] = Action { request =>
    Requests.get[User](id, request, cookingApi, cookingApi.users)
  }
  def put(id: java.util.UUID): Action[JsValue] = Action(parse.json) { request =>
    Requests.put[User](id, request, cookingApi, cookingApi.users)
  }
}
