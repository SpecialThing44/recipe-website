package api.users

import com.google.inject.Inject
import context.ApiContext
import domain.users.User
import persistence.users.Users
import zio.ZIO

import java.util.UUID

class UserDeleteInteractor @Inject()(
    persistence: Users,
    authenticationInteractor: AuthenticationInteractor,
) {
  def delete(id: UUID): ZIO[ApiContext, Throwable, User] = for {
    context <- ZIO.service[ApiContext]
    user <- persistence.getById(id)
    _ <- authenticationInteractor.ensureAuthenticatedAndMatchingUser(
      context.applicationContext.user,
      user.id
    )
    deletedUser <- persistence.delete(id)
  } yield deletedUser
}
