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
      authHeader <- ZIO.succeed(request.headers.get("Authorization"))
      maybeUser <- authHeader match {
        case Some(auth) =>
          val bearerToken = OAuth2BearerToken(auth)
          cookingApi.users.authenticate(bearerToken)
        case None => ZIO.succeed(None)
      }
    } yield maybeUser

    ApiRunner.runResponse[ApiContext, Serializable, Option[User]](
      maybeUserZio,
      cookingApi,
      None
    )
  }
}
