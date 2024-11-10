package http.authentication

import com.google.inject.{Inject, Singleton}
import context.CookingApi
import domain.people.users.{LoginInput, UserInput}
import http.ApiRunner
import play.api.mvc.{
  AbstractController,
  Action,
  AnyContent,
  ControllerComponents
}
import play.api.libs.json.{JsValue, Json}
import io.circe.parser.decode

@Singleton
class AuthenticationController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi
) extends AbstractController(cc) {
  def signup(): Action[JsValue] = Action(parse.json) { request =>
    decode[UserInput](request.body.toString()) match {
      case Right(user) =>
        val token = ApiRunner.runResponse(
          cookingApi.users.signup(user, cookingApi),
          cookingApi,
          None
        )
        Ok(Json.obj("token" -> token))
      case Left(error) =>
        BadRequest(Json.obj("error" -> error.getMessage))
    }
  }

  def login(): Action[JsValue] = Action(parse.json) { request =>
    decode[LoginInput](request.body.toString()) match {
      case Right(loginInput) =>
        val maybeToken = ApiRunner.runResponse(
          cookingApi.users.login(loginInput.email, loginInput.password),
          cookingApi,
          None
        )
        maybeToken match {
          case Some(token) => Ok(Json.obj("token" -> token))
          case None        => Unauthorized("Invalid credentials")
        }
      case _ => BadRequest(Json.obj("error" -> "Invalid input"))
    }
  }

  def logout(): Action[AnyContent] = Action { request =>
    val result =
      ApiRunner.runResponse(
        cookingApi.users.logout(request),
        cookingApi,
        None
      )
    if (result) Ok("Logged out successfully")
    else Unauthorized("Invalid token")
  }
}
