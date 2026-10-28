package http

import domain.types.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ErrorMappingSpec extends AnyFlatSpec with Matchers {
  it should "distinguish authentication authorization validation and missing entities" in {
    Seq(
      AuthenticationError("Sign in") -> 401,
      AuthorizationError("Forbidden") -> 403,
      InputError("Bad input") -> 400,
      new IllegalArgumentException("Bad input") -> 400,
      NotFoundError("Missing") -> 404,
      ServiceBusyError("Busy") -> 503
    ).foreach { case (error, status) =>
      ErrorMapping.mapCustomErrorsToHttp(error).header.status shouldBe status
    }
  }

  it should "hide internal exception details in server errors" in {
    val response = ErrorMapping.mapCustomErrorsToHttp(
      SystemError("secret upstream credentials")
    )
    response.header.status shouldBe 500
    response.body
      .asInstanceOf[play.api.http.HttpEntity.Strict]
      .data
      .utf8String should not include "secret upstream credentials"
  }
}
