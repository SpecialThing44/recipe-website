package domain.users

import domain.shared.Identified
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.time.Instant
import java.util.UUID

case class AvatarUrls(
    thumbnail: String,
    medium: String,
    large: String
)

case class User(
    name: String,
    email: String,
    countryOfOrigin: Option[String] = None,
    avatar: Option[AvatarUrls] = None,
    admin: Boolean = false,
    createdOn: Instant,
    updatedOn: Instant,
    id: UUID
) extends Identified {}

case class UserInput(
    name: String,
    email: String,
    password: String = "",
    countryOfOrigin: Option[String] = None,
)

case class UserUpdateInput(
    name: Option[String] = None,
    email: Option[String] = None,
    countryOfOrigin: Option[String] = None,
    avatar: Option[AvatarUrls] = None,
)

case class LoginInput(email: String, password: String)

object AvatarUrls {
  implicit val encoder: Encoder[AvatarUrls] = deriveEncoder[AvatarUrls]
  implicit val decoder: Decoder[AvatarUrls] = deriveDecoder[AvatarUrls]
}

object User {
  implicit val encoder: Encoder[User] = deriveEncoder[User]
  implicit val decoder: Decoder[User] = deriveDecoder[User]

  def empty(): User = {
    val now = Instant.now
    User("", "", None, None, false, now, now, UUID.randomUUID())
  }
}

object UserInput {
  implicit val encoder: Encoder[UserInput] = deriveEncoder[UserInput]
  implicit val decoder: Decoder[UserInput] = deriveDecoder[UserInput]
}

object UserUpdateInput {
  implicit val encoder: Encoder[UserUpdateInput] =
    deriveEncoder[UserUpdateInput]
  implicit val decoder: Decoder[UserUpdateInput] =
    deriveDecoder[UserUpdateInput]
}

object LoginInput {
  implicit val encoder: Encoder[LoginInput] = deriveEncoder[LoginInput]
  implicit val decoder: Decoder[LoginInput] = deriveDecoder[LoginInput]
}
