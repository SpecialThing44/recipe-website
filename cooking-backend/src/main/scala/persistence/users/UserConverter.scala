package persistence.users

import domain.users.User
import persistence.Converter

import java.time.Instant
import java.util
import java.util.UUID

object UserConverter extends Converter[User] {
  val emailField = "email"
  val countryOfOriginField = "countryOfOrigin"
  val avatarUrlField = "avatarUrl"
  override def toGraph(user: User): Map[String, Object] =
    Map(
      idField -> user.id.toString,
      nameField -> user.name,
      lowerPrefix + nameField -> user.name.toLowerCase,
      emailField -> user.email,
      lowerPrefix + emailField -> user.email.toLowerCase,
      countryOfOriginField -> user.countryOfOrigin.getOrElse(""),
      avatarUrlField -> user.avatarUrl.getOrElse(""),
      createdOnField -> user.createdOn.toString,
      updatedOnField -> user.updatedOn.toString
    )

  override def toDomain(record: util.Map[String, AnyRef]): User = User(
    id = UUID.fromString(record.get(idField).toString),
    name = record.get(nameField).toString,
    email = record.get(emailField).toString,
    countryOfOrigin = Option(record.get(countryOfOriginField).toString).filter(_.nonEmpty),
    avatarUrl = Option(record.get(avatarUrlField).toString).filter(_.nonEmpty),
    createdOn = Instant.parse(record.get(createdOnField).toString),
    updatedOn = Instant.parse(record.get(updatedOnField).toString)
  )
}
