package http

import domain.logging.Logging
import domain.types.*
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Result
import play.api.mvc.Results.{
  BadRequest,
  Forbidden,
  InternalServerError,
  NotFound,
  Unauthorized,
  ServiceUnavailable
}

object ErrorMapping extends Logging {

  def mapCustomErrorsToHttp(error: Throwable): Result = {
    error match {
      case NotFoundError(message)       => NotFound(errorJson(message))
      case NoSuchEntityError(message)   => NotFound(errorJson(message))
      case InputError(message)          => BadRequest(errorJson(message))
      case AuthenticationError(message) => Unauthorized(errorJson(message))
      case AuthorizationError(message)  => Forbidden(errorJson(message))
      case ServiceBusyError(message) => ServiceUnavailable(errorJson(message))
      case _: io.circe.Error => BadRequest(errorJson("Invalid request JSON"))
      case _: IllegalArgumentException =>
        BadRequest(errorJson("Invalid request input"))
      case _ =>
        logger.error(
          s"${error.toString}\n${error.getStackTrace.mkString("\n")}"
        )
        InternalServerError(errorJson("An internal error occurred"))
    }
  }

  def errorJson(message: String): JsObject = Json.obj("error" -> message)

  def messageJson(message: String): JsObject = Json.obj("message" -> message)
}
