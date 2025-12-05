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
      UUID.randomUUID().toString,
      user.countryOfOrigin,
      None,
      false, // admin defaults to false for new users
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
      existingUser.identity,
      if (user.countryOfOrigin.isDefined) user.countryOfOrigin
      else existingUser.countryOfOrigin,
      if (user.avatar.isDefined) user.avatar
      else existingUser.avatar,
      existingUser.admin, // preserve admin status
      existingUser.createdOn,
      now,
      existingUser.id
    )
  }
}
