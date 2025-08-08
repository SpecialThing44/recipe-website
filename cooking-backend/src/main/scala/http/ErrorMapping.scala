package http

import domain.types.*
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Result
import play.api.mvc.Results.{Forbidden, InternalServerError, NotFound}

object ErrorMapping {

  def mapCustomErrorsToHttp(error: Throwable): Result = {
    error match {
      case NotFoundError(message) => Forbidden(errorJson(message))
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
