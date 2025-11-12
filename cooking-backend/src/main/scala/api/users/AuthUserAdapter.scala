package api.users

import domain.users.UserInput
import domain.authentication.AuthUser

import java.time.Instant
import java.util.UUID

object AuthUserAdapter {
  def adapt(user: UserInput): AuthUser = {
    val now = Instant.now
    AuthUser(
      UUID.randomUUID(),
      user.password,
    )
  }
}
