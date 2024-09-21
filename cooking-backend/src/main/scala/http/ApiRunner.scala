package http

import context.{ApiContext, ApplicationContext, CookingApi}
import domain.people.users.User
import domain.types.ZIORuntime
import play.api.mvc.Result
import zio.{URIO, ZEnvironment}

object ApiRunner {
  def runResponse(
      response: URIO[ApiContext, Result],
      cookingApi: CookingApi,
      maybeUser: Option[User]
  ): Result =
    ZIORuntime.unsafeRun(
      response.provideEnvironment(
        ZEnvironment(
          ApiContext.apply(cookingApi, ApplicationContext.apply(maybeUser))
        )
      )
    )

}
