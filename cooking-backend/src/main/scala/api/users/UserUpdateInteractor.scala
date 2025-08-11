package api.users

import com.google.inject.Inject
import context.ApiContext
import domain.users.{User, UserUpdateInput}
import persistence.users.Users
import zio.ZIO

class UserUpdateInteractor @Inject() (
    persistence: Users,
) {
  def update(
      entity: UserUpdateInput,
      originalEntity: User
  ): ZIO[ApiContext, Throwable, User] =
    for {
      context <- ZIO.service[ApiContext]
      _ <- AuthenticationInteractor.ensureAuthenticatedAndMatchingUser(
        context.applicationContext.user,
        originalEntity.id
      )
      updatedUser <- persistence.update(
        UserAdapter.adaptUpdate(entity, originalEntity),
        originalEntity
      )
    } yield updatedUser
}
