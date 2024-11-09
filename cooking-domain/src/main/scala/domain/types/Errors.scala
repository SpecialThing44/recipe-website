package domain.types

case class NotFoundError(message: String) extends Throwable(message)
case class AuthenticationError(message: String) extends Throwable(message)
case class SystemError(message: String) extends Throwable(message)
