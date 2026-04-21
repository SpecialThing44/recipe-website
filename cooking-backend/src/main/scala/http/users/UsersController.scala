package http.users

import com.google.inject.{Inject, Singleton}
import context.CookingApi
import domain.users.{User, UserInput, UserUpdateInput}
import http.Requests
import http.Requests.extractUser
import org.apache.pekko.util.ByteString
import play.api.libs.json.JsValue
import play.api.mvc.*

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UsersController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi
) extends AbstractController(cc) {
  import http.{ApiRunner, ErrorMapping}
  import io.circe.syntax.EncoderOps
  import play.api.libs.json.Json
  private implicit val ec: ExecutionContext = cc.executionContext

  def getCurrentUser: Action[AnyContent] = Action.async { request =>
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
    ApiRunner.runResponseAsyncSafely(response, cookingApi, None)
  }

  def get(id: java.util.UUID): Action[AnyContent] = Action.async { request =>
    Requests.get[User](id, request, cookingApi, cookingApi.users)(User.encoder)
  }
  def list(): Action[JsValue] = Action.async(parse.json) { request =>
    Requests.list[User](request, cookingApi, cookingApi.users)
  }
  def put(id: java.util.UUID): Action[JsValue] = Action.async(parse.json) {
    request =>
      Requests.put[User, UserInput, UserUpdateInput](
        id,
        request,
        cookingApi,
        cookingApi.users
      )
  }
  def delete(id: java.util.UUID): Action[AnyContent] = Action.async { request =>
    Requests.delete[User](id, request, cookingApi, cookingApi.users)(
      User.encoder
    )
  }

  def uploadAvatar(id: java.util.UUID): Action[AnyContent] = Action.async {
    request =>
      extractUser(request, cookingApi).flatMap { maybeUser =>
        request.body.asRaw match {
          case Some(raw) =>
            val fileBytes = raw.asBytes().getOrElse(ByteString.empty)
            val contentType = request.contentType.getOrElse("image/jpeg")

            if (fileBytes.isEmpty) {
              Future.successful(
                BadRequest(Json.obj("error" -> "No file data provided"))
              )
            } else {
              val response = cookingApi.users
                .uploadAvatar(id, fileBytes, contentType)
                .fold(
                  error => ErrorMapping.mapCustomErrorsToHttp(error),
                  user =>
                    Ok(s"{ \"Body\": ${Json.parse(user.asJson.noSpaces)} }")
                )
              ApiRunner.runResponseAsyncSafely(response, cookingApi, maybeUser)
            }
          case None =>
            Future.successful(
              BadRequest(Json.obj("error" -> "No file uploaded"))
            )
        }
      }
  }
}
