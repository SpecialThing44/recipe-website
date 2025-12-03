package persistence.users

import domain.users.{User, AvatarUrls}
import persistence.Converter

import java.time.Instant
import java.util
import java.util.UUID

object UserConverter extends Converter[User] {
  val emailField = "email"
  val countryOfOriginField = "countryOfOrigin"
  val avatarUrlField = "avatarUrl"
  val avatarThumbnailUrlField = "avatarThumbnailUrl"
  val avatarMediumUrlField = "avatarMediumUrl"
  
  override def toGraph(user: User): Map[String, Object] =
    Map(
      idField -> user.id.toString,
      nameField -> user.name,
      lowerPrefix + nameField -> user.name.toLowerCase,
      emailField -> user.email,
      lowerPrefix + emailField -> user.email.toLowerCase,
      countryOfOriginField -> user.countryOfOrigin.getOrElse(""),
      avatarUrlField -> user.avatar.map(_.large).getOrElse(""),
      avatarThumbnailUrlField -> user.avatar.map(_.thumbnail).getOrElse(""),
      avatarMediumUrlField -> user.avatar.map(_.medium).getOrElse(""),
      createdOnField -> user.createdOn.toString,
      updatedOnField -> user.updatedOn.toString
    )

  override def toDomain(record: util.Map[String, AnyRef]): User = {
    val thumbnail = Option(record.get(avatarThumbnailUrlField).toString).filter(_.nonEmpty)
    val medium = Option(record.get(avatarMediumUrlField).toString).filter(_.nonEmpty)
    val large = Option(record.get(avatarUrlField).toString).filter(_.nonEmpty)
    
    val avatar = (thumbnail, medium, large) match {
      case (Some(t), Some(m), Some(l)) => Some(AvatarUrls(t, m, l))
      case _ => None
    }
    
    User(
      id = UUID.fromString(record.get(idField).toString),
      name = record.get(nameField).toString,
      email = record.get(emailField).toString,
      countryOfOrigin = Option(record.get(countryOfOriginField).toString).filter(_.nonEmpty),
      avatar = avatar,
      createdOn = Instant.parse(record.get(createdOnField).toString),
      updatedOn = Instant.parse(record.get(updatedOnField).toString)
    )
  }
}
