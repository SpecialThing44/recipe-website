package http

import context.{ApiContext, ApplicationContext, CookingApi}
import domain.people.users.User
import domain.types.ZIORuntime
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
}
