package http

import play.api.mvc.Result
import play.api.mvc.Results.{Forbidden, InternalServerError}

case class NotFoundError(message: String) extends Throwable(message)

object ErrorMapping {

  def mapCustomErrorsToHttp(error: Throwable): Result = {
    error match {
      case NotFoundError(message) => Forbidden(message)
      case _                      => InternalServerError(error.getMessage)
    }
  }
}
