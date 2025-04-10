package api.users

import domain.people.users.{User, UserInput}

import java.time.Instant

object UserAdapter {
  def adapt(user: UserInput): User = {
    val now = Instant.now
    User(
      user.name,
      user.email,
      user.password,
      Seq.empty,
      user.countryOfOrigin,
      now,
      now,
      None
    )
  }

  def adaptUpdate(user: UserInput, existingUser: User): User = {
    val now = Instant.now
    User(
      user.name,
      user.email,
      existingUser.password,
      Seq.empty,
      user.countryOfOrigin,
      existingUser.createdOn,
      now,
      existingUser.id
    )
  }
}
