package http.authentication

import com.google.inject.{Inject, Singleton}
import context.CookingApi
import domain.authentication.TokenPair
import domain.users.{LoginInput, UserInput}
import http.ErrorMapping.{errorJson, messageJson}
import http.{ApiRunner, ErrorMapping}
import io.circe.parser.decode
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{
  AbstractController,
  Action,
  AnyContent,
  ControllerComponents,
  Cookie
}

@Singleton
class AuthenticationController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi
) extends AbstractController(cc) {

  private val REFRESH_TOKEN_MAX_AGE = 7 * 24 * 60 * 60 // 7 days in seconds

  private def setTokenCookies(tokenPair: TokenPair) = {
    val refreshTokenCookie = Cookie(
      name = "refresh_token",
      value = tokenPair.refreshToken,
      maxAge = Some(REFRESH_TOKEN_MAX_AGE),
      httpOnly = true,
      secure = false, // Set to true in production with HTTPS
      sameSite = Some(Cookie.SameSite.Strict)
    )
    
    Ok(Json.obj(
      "accessToken" -> tokenPair.accessToken,
      "message" -> "Authentication successful"
    )).withCookies(refreshTokenCookie)
  }

  def signup(): Action[JsValue] = Action(parse.json) { request =>
    decode[UserInput](request.body.toString()) match {
      case Right(user) =>
        try {
          val tokenPair = ApiRunner.runResponse(
            cookingApi.users.signup(user),
            cookingApi,
            None
          )
          setTokenCookies(tokenPair)
        } catch {
          case e: Throwable =>
            ErrorMapping.mapCustomErrorsToHttp(e)
        }
      case Left(error) =>
        BadRequest(Json.obj("error" -> error.getMessage))
    }
  }

  def login(): Action[JsValue] = Action(parse.json) { request =>
    decode[LoginInput](request.body.toString()) match {
      case Right(loginInput) =>
        try {
          val maybeTokenPair = ApiRunner.runResponse(
            cookingApi.users.login(loginInput.email, loginInput.password),
            cookingApi,
            None
          )
          maybeTokenPair match {
            case Some(tokenPair) => setTokenCookies(tokenPair)
            case None => Unauthorized(errorJson("Invalid credentials"))
          }
        } catch {
          case e: Throwable =>
            ErrorMapping.mapCustomErrorsToHttp(e)
        }
      case _ => BadRequest(errorJson("Invalid input"))
    }
  }

  def logout(): Action[AnyContent] = Action { request =>
    val result =
      ApiRunner.runResponse(
        cookingApi.users.logout(request),
        cookingApi,
        None
      )
    if (result) {
      Ok(messageJson("Logged out successfully"))
        .discardingCookies(play.api.mvc.DiscardingCookie("refresh_token"))
    } else {
      Unauthorized(errorJson("Invalid token"))
    }
  }

  def refresh(): Action[AnyContent] = Action { request =>
    request.cookies.get("refresh_token") match {
      case Some(cookie) =>
        try {
          val maybeTokenPair = ApiRunner.runResponse(
            cookingApi.users.refresh(cookie.value),
            cookingApi,
            None
          )
          maybeTokenPair match {
            case Some(tokenPair) => setTokenCookies(tokenPair)
            case None => Unauthorized(errorJson("Invalid or expired refresh token"))
          }
        } catch {
          case e: Throwable =>
            ErrorMapping.mapCustomErrorsToHttp(e)
        }
      case None =>
        Unauthorized(errorJson("No refresh token provided"))
    }
  }
}
