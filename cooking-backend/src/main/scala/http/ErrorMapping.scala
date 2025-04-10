package http

import play.api.mvc.Result
import play.api.mvc.Results.{Forbidden, InternalServerError}
import domain.types._

object ErrorMapping {

  def mapCustomErrorsToHttp(error: Throwable): Result = {
    error match {
      case NotFoundError(message)       => Forbidden(message)
      case AuthenticationError(message) => Forbidden(message)
      case SystemError(message)         => InternalServerError(message)
      case _                            => InternalServerError(error.getMessage)
    }
  }
}
