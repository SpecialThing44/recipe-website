package domain.types

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class NotFoundError(message: String) extends Throwable(message)
case class AuthenticationError(message: String) extends Throwable(message)
case class NoSuchEntityError(message: String) extends Throwable(message)
case class SystemError(message: String) extends Throwable(message)

sealed trait Fault {
  def code: String

  def description: String
}

final case class StandardFault(code: String, description: String) extends Fault

object StandardFault {
  implicit val StandardFaultEncoder: Encoder[StandardFault] =
    deriveEncoder[StandardFault]
  implicit val StandardFaultDecoder: Decoder[StandardFault] =
    deriveDecoder[StandardFault]
}

object Fault {
  def apply(code: String, description: String): StandardFault =
    StandardFault(code, description)
}
