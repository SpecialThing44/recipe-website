package persistence.authentication

import domain.users.User
import persistence.Converter

import java.time.Instant
import java.util
import java.util.UUID

object AuthUserConverter extends Converter[AuthUser] {
  val passwordField = "password"
  val saltField = "salt"
  override def toGraph(user: AuthUser): Map[String, Object] =
    Map(
      idField -> user.id.toString,
      saltField -> user.salt,
      passwordField -> user.password,
    )

  override def toDomain(record: util.Map[String, AnyRef]): AuthUser = AuthUser(
    id = UUID.fromString(record.get(idField).toString),
    salt = record.get(saltField).toString,
    password = record.get(passwordField).toString,

  )
}
