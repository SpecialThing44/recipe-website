package http.authentication

import context.{ApiContext, CookingApi}
import domain.people.users.User
import http.ApiRunner
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import play.api.mvc.Request
import zio.ZIO

object UserAuthentication {
  def getMaybeUser(
      request: Request[?],
      cookingApi: CookingApi
  ): Option[User] = {
    val maybeUserZio = for {
      auth <- ZIO
        .fromOption(request.headers.get("Authorization"))
        .orElseFail(new Throwable("Authorization header missing"))
      bearerToken = OAuth2BearerToken(auth)
      maybeUser <- cookingApi.users.authenticate(bearerToken)
    } yield maybeUser
    ApiRunner.runResponse[ApiContext, Throwable, Option[User]](
      maybeUserZio,
      cookingApi,
      None
    )
  }
}
