package domain.people.users

import domain.shared.Identified
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.time.Instant
import java.util.UUID

case class User(
    name: String,
    email: String,
    savedRecipes: Seq[UUID] = Seq.empty,
    countryOfOrigin: Option[String] = None,
    createdOn: Instant,
    updatedOn: Instant,
    id: Option[UUID]
) extends Identified {}

case class UserInput(
    name: String,
    email: String,
    countryOfOrigin: Option[String] = None,
)

case class LoginInput(email: String, password: String)

object User {
  implicit val encoder: Encoder[User] = deriveEncoder[User]
  implicit val decoder: Decoder[User] = deriveDecoder[User]

  def empty(): User = {
    val now = Instant.now
    User("", "", Seq.empty, None, now, now, Some(UUID.randomUUID()))
  }
}

object UserInput {
  implicit val encoder: Encoder[UserInput] = deriveEncoder[UserInput]
  implicit val decoder: Decoder[UserInput] = deriveDecoder[UserInput]
}

object LoginInput {
  implicit val encoder: Encoder[LoginInput] = deriveEncoder[LoginInput]
  implicit val decoder: Decoder[LoginInput] = deriveDecoder[LoginInput]
}