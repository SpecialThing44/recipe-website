package persistence.users

import domain.users.User
import persistence.Converter

import java.time.Instant
import java.util
import java.util.UUID

object UserConverter extends Converter[User] {
  val emailField = "email"
  val passwordField = "password"
  val countryOfOriginField = "countryOfOrigin"
  override def toGraph(user: User): Map[String, Object] =
    Map(
      idField -> user.id.toString,
      nameField -> user.name,
      lowerPrefix + nameField -> user.name.toLowerCase,
      emailField -> user.email,
      lowerPrefix + emailField -> user.email.toLowerCase,
      passwordField -> user.password,
      countryOfOriginField -> user.countryOfOrigin.getOrElse(""),
      createdOnField -> user.createdOn.toString,
      updatedOnField -> user.updatedOn.toString
    )

  override def toDomain(record: util.Map[String, AnyRef]): User = User(
    id = UUID.fromString(record.get(idField).toString),
    name = record.get(nameField).toString,
    email = record.get(emailField).toString,
    password = "",
    countryOfOrigin = Option(record.get(countryOfOriginField).toString),
    createdOn = Instant.parse(record.get(createdOnField).toString),
    updatedOn = Instant.parse(record.get(updatedOnField).toString)
  )
}
