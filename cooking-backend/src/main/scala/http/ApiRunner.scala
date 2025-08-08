package http

import context.{ApiContext, ApplicationContext, CookingApi}
import domain.types.ZIORuntime
import domain.users.User
import play.api.mvc.Result
import zio.{ZEnvironment, ZIO}

object ApiRunner {

  def runResponse[R, E, ResponseType](
      response: ZIO[R, E, ResponseType],
      cookingApi: CookingApi,
      maybeUser: Option[User]
  )(implicit ev: R <:< ApiContext): ResponseType = {
    ZIORuntime.unsafeRun(
      response.provideEnvironment(
        ZEnvironment(
          ApiContext.apply(cookingApi, ApplicationContext.apply(maybeUser))
        ).asInstanceOf[ZEnvironment[R]]
      )
    )
  }

  def runResponseSafely[R](
      response: ZIO[R, Throwable, Result],
      cookingApi: CookingApi,
      maybeUser: Option[User]
  )(implicit ev: R <:< ApiContext): Result = {
    try {
      runResponse(response, cookingApi, maybeUser)
    } catch {
      case e: Throwable =>
        ErrorMapping.mapCustomErrorsToHttp(e)
    }
  }
}
