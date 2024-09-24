package domain.people.users

import domain.food.recipes.Recipe
import domain.shared.Identified
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.time.Instant
import java.util.UUID

case class User(
    name: String,
    email: String,
    recipes: Seq[Recipe],
    savedRecipes: Seq[Recipe],
    countryOfOrigin: Option[String], // Type!
    createdOn: Option[Instant] = None,
    updatedOn: Option[Instant] = None,
    id: UUID
) extends Identified {}

object User {
  implicit val encoder: Encoder[User] = deriveEncoder[User]
  implicit val decoder: Decoder[User] = deriveDecoder[User]

  def empty(): User =
    User("", "", Seq.empty, Seq.empty, None, None, None, UUID.randomUUID())
}
