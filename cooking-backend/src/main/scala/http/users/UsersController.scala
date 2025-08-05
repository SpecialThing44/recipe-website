package http.users

import com.google.inject.{Inject, Singleton}
import context.CookingApi
import domain.people.users.{User, UserInput, UserUpdateInput}
import http.Requests
import play.api.libs.json.JsValue
import play.api.mvc.*

@Singleton
class UsersController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi
) extends AbstractController(cc) {
  def get(id: java.util.UUID): Action[AnyContent] = Action { request =>
    Requests.get[User](id, request, cookingApi, cookingApi.users)(User.encoder)
  }
  def list(): Action[JsValue] = Action(parse.json) { request =>
    Requests.list[User](request, cookingApi, cookingApi.users)
  }
  def put(id: java.util.UUID): Action[JsValue] = Action(parse.json) { request =>
    Requests.put[User, UserInput, UserUpdateInput](
      id,
      request,
      cookingApi,
      cookingApi.users
    )
  }
  def delete(id: java.util.UUID): Action[AnyContent] = Action { request =>
    Requests.delete[User](id, request, cookingApi, cookingApi.users)(
      User.encoder
    )
  }
}
