package api.users

import domain.people.users.{User, UserInput}

import java.time.Instant

object UserAdapter {
  def adapt(user: UserInput): User = {
    val now = Instant.now
    User(user.name, user.email, Seq.empty, user.countryOfOrigin, now, now, None)
  }
}
