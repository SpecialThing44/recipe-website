package persistence.authentication

import domain.authentication.AuthUser
import persistence.Converter

import java.util
import java.util.UUID

object AuthUserConverter extends Converter[AuthUser] {
  val passwordField = "password"
  override def toGraph(user: AuthUser): Map[String, Object] =
    Map(
      idField -> user.id.toString,
      passwordField -> user.passwordHash,
    )

  override def toDomain(record: util.Map[String, AnyRef]): AuthUser = AuthUser(
    id = UUID.fromString(record.get(idField).toString),
    passwordHash = record.get(passwordField).toString,

  )
}
