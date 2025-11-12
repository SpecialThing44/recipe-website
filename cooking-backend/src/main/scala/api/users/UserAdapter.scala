package api.users

import domain.users.{User, UserInput, UserUpdateInput}

import java.time.Instant
import java.util.UUID

object UserAdapter {
  def adapt(user: UserInput): User = {
    val now = Instant.now
    User(
      user.name,
      user.email,
      user.countryOfOrigin,
      now,
      now,
      UUID.randomUUID()
    )
  }

  def adaptUpdate(user: UserUpdateInput, existingUser: User): User = {
    val now = Instant.now
    User(
      user.name.getOrElse(existingUser.name),
      user.email.getOrElse(existingUser.email),
      if (user.countryOfOrigin.isDefined) user.countryOfOrigin
      else existingUser.countryOfOrigin,
      existingUser.createdOn,
      now,
      existingUser.id
    )
  }
}
