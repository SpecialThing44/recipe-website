package persistence.users

import domain.users.User
import persistence.Converter

import java.time.Instant
import java.util
import java.util.UUID

object UserConverter extends Converter[User] {
  override def toGraph(user: User): Map[String, Object] =
    Map(
      "id" -> user.id,
      "name" -> user.name,
      s"${lowerPrefix}name" -> user.name.toLowerCase,
      "email" -> user.email,
      s"${lowerPrefix}email" -> user.email.toLowerCase,
      "password" -> user.password,
      "country_of_origin" -> user.countryOfOrigin.getOrElse(""),
      "created_on" -> user.createdOn.toString,
      "updated_on" -> user.updatedOn.toString
    )

  override def toDomain(record: util.Map[String, AnyRef]): User = User(
    id = UUID.fromString(record.get("id").toString),
    name = record.get("name").toString,
    email = record.get("email").toString,
    password = "",
    countryOfOrigin = Option(record.get("country_of_origin").toString),
    createdOn = Instant.parse(record.get("created_on").toString),
    updatedOn = Instant.parse(record.get("updated_on").toString)
  )

  def toAuthDomain(record: util.Map[String, AnyRef]): User = User(
    id = UUID.fromString(record.get("id").toString),
    name = record.get("name").toString,
    email = record.get("email").toString,
    password = record.get("password").toString,
    countryOfOrigin = Option(record.get("country_of_origin").toString),
    createdOn = Instant.parse(record.get("created_on").toString),
    updatedOn = Instant.parse(record.get("updated_on").toString)
  )
}
