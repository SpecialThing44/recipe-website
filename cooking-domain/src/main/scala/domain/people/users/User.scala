package domain.people.users

import domain.food.recipes.Recipe
import domain.shared.Identified

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
  def empty(): User =
    User("", "", Seq.empty, Seq.empty, None, None, None, UUID.randomUUID())
}
