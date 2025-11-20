package http.users

import com.google.inject.{Inject, Singleton}
import context.CookingApi
import domain.users.{User, UserInput, UserUpdateInput}
import http.Requests
import http.Requests.extractUser
import org.apache.pekko.util.ByteString
import play.api.libs.json.JsValue
import play.api.mvc.*

@Singleton
class UsersController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi
) extends AbstractController(cc) {
  import http.{ApiRunner, ErrorMapping}
  import io.circe.syntax.EncoderOps
  import play.api.libs.json.Json

  def getCurrentUser: Action[AnyContent] = Action { request =>
    val authHeader = request.headers.get("Authorization")
    val maybeUser = cookingApi.users.authenticate(authHeader)
    val response = maybeUser.fold(
      error => ErrorMapping.mapCustomErrorsToHttp(error),
      {
        case Some(user) =>
          Ok(s"{ \"Body\": ${Json.parse(user.asJson.noSpaces)} }")
        case None =>
          Unauthorized(Json.obj("error" -> "Invalid or missing token"))
      }
    )
    ApiRunner.runResponseSafely(response, cookingApi, None)
  }

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

  def uploadAvatar(id: java.util.UUID): Action[AnyContent] = Action { request =>
    val maybeUser = extractUser(request, cookingApi)

    request.body.asRaw match {
      case Some(raw) =>
        val fileBytes = raw.asBytes().getOrElse(ByteString.empty)
        val contentType = request.contentType.getOrElse("image/jpeg")

        if (fileBytes.isEmpty) {
          BadRequest(Json.obj("error" -> "No file data provided"))
        } else {
          val result = cookingApi.users.uploadAvatar(id, fileBytes, contentType)
          val response = result.fold(
            error => ErrorMapping.mapCustomErrorsToHttp(error),
            user => Ok(s"{ \"Body\": ${Json.parse(user.asJson.noSpaces)} }")
          )
          ApiRunner.runResponseSafely(response, cookingApi, maybeUser)
        }
      case None =>
        BadRequest(Json.obj("error" -> "No file uploaded"))
    }
  }
}
