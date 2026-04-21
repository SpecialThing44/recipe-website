package http

import context.{ApiContext, ApplicationContext, CookingApi}
import domain.users.User
import play.api.mvc.Result
import zio.{Unsafe, ZEnvironment, ZIO}

import scala.concurrent.{ExecutionContext, Future}

object ApiRunner {

  private def runToFuture[A](app: ZIO[Any, Throwable, A]): Future[A] =
    Unsafe.unsafe { implicit unsafe =>
      zio.Runtime.default.unsafe.runToFuture(app)
    }

  def runResponseAsync[R, E <: Throwable, ResponseType](
      response: ZIO[R, E, ResponseType],
      cookingApi: CookingApi,
      maybeUser: Option[User]
  )(implicit
      ev: R <:< ApiContext,
      ec: ExecutionContext
  ): Future[ResponseType] = {
    runToFuture(
      response.provideEnvironment(
        ZEnvironment(
          ApiContext.apply(cookingApi, ApplicationContext.apply(maybeUser))
        ).asInstanceOf[ZEnvironment[R]]
      )
    )
  }

  def runResponseAsyncSafely[R](
      response: ZIO[R, Throwable, Result],
      cookingApi: CookingApi,
      maybeUser: Option[User]
  )(implicit ev: R <:< ApiContext, ec: ExecutionContext): Future[Result] = {
    runResponseAsync(response, cookingApi, maybeUser).recover {
      case e: Throwable =>
        ErrorMapping.mapCustomErrorsToHttp(e)
    }
  }
}
