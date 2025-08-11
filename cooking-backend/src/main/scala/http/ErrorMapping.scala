package http

import domain.logging.Logging
import domain.types.*
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Result
import play.api.mvc.Results.{BadRequest, Forbidden, InternalServerError, NotFound}

object ErrorMapping extends Logging {

  def mapCustomErrorsToHttp(error: Throwable): Result = {
    logger.info(error.getMessage)
    logger.info(error.getStackTrace.mkString("\n"))
    error match {
      case NotFoundError(message) => Forbidden(errorJson(message))
      case InputError(message) => BadRequest(errorJson(message))
      case AuthenticationError(message) =>
        Forbidden(errorJson(message))
      case NoSuchEntityError(message) => NotFound(errorJson(message))
      case SystemError(message) =>
        InternalServerError(errorJson(message))
      case _ => InternalServerError(errorJson(error.getMessage))
    }
  }

  def errorJson(message: String): JsObject = Json.obj("error" -> message)

  def messageJson(message: String): JsObject = Json.obj("message" -> message)
}
