package http.users

import com.google.inject.{Inject, Singleton}
import context.CookingApi
import domain.people.users.User
import http.Requests
import io.circe.Decoder
import play.api.libs.json.JsValue
import play.api.mvc._

@Singleton
class UsersController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi
) extends AbstractController(cc) {
  def get(id: java.util.UUID): Action[JsValue] = Action(parse.json) { request =>
    Requests.getById[User](id, request, cookingApi, cookingApi.users)
  }
  def put(id: java.util.UUID): Action[JsValue] = Action(parse.json) { request =>
    implicit val recipeDecoder: Decoder[User] = User.decoder
    Requests.put[User](id, request, cookingApi, cookingApi.users)
  }
}
