package http.authentication

import context.{ApiContext, ApplicationContext, CookingApi}
import domain.people.users.User
import domain.types.ZIORuntime
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import play.api.mvc.Request
import zio.{ZEnvironment, ZIO}

object UserAuthentication {
  def getMaybeUser(
      request: Request[_],
      cookingApi: CookingApi
  ): Option[User] = {
    val maybeUserZio = for {
      auth <- ZIO
        .fromOption(request.headers.get("Authorization"))
        .orElseFail(new Throwable("Authorization header missing"))
      bearerToken = OAuth2BearerToken(auth)
      maybeUser <- cookingApi.users.authenticate(bearerToken)
    } yield maybeUser
    ZIORuntime.unsafeRun(
      maybeUserZio.provideEnvironment(
        ZEnvironment(
          ApiContext.apply(cookingApi, ApplicationContext.apply(None))
        )
      )
    )
  }
}
