package api.users

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.users.{User, UserInput, UserUpdateInput}
import org.apache.pekko.util.ByteString
import persistence.users.Users
import zio.ZIO

import java.time.Instant
import java.util.UUID

class UserFacade @Inject() (
    persistence: Users,
    authenticationInteractor: AuthenticationInteractor,
    deleteInteractor: UserDeleteInteractor,
    updateInteractor: UserUpdateInteractor,
    fetchInteractor: UserFetchInteractor,
    getInteractor: UserGetInteractor,
    avatarInteractor: UserAvatarInteractor
) extends UserApi {

  override def create(
      entity: UserInput
  ): ZIO[ApiContext, Throwable, User] = {
    val user = User(
      name = entity.name,
      email = entity.email,
      identity = UUID.randomUUID().toString,
      countryOfOrigin = entity.countryOfOrigin,
      createdOn = Instant.now(),
      updatedOn = Instant.now(),
      id = UUID.randomUUID()
    )
    persistence.create(user)
  }

  override def update(
      entity: UserUpdateInput,
      originalEntity: User
  ): ZIO[ApiContext, Throwable, User] =
    updateInteractor.update(entity, originalEntity)

  override def delete(id: UUID): ZIO[ApiContext, Throwable, User] =
    deleteInteractor.delete(id)

  override def deleteAll(): ZIO[ApiContext, Throwable, Unit] =
    deleteInteractor.deleteAll()

  override def list(filters: Filters): ZIO[ApiContext, Throwable, Seq[User]] =
    fetchInteractor.list(filters)

  override def getById(id: UUID): ZIO[ApiContext, Throwable, User] =
    getInteractor.getById(id)

  override def authenticate(
      bearerToken: Option[String]
  ): ZIO[ApiContext, Throwable, Option[User]] =
    authenticationInteractor.getMaybeUser(bearerToken)

  override def uploadAvatar(
      userId: UUID,
      fileBytes: ByteString,
      contentType: String
  ): ZIO[ApiContext, Throwable, User] =
    avatarInteractor.uploadAvatar(userId, fileBytes, contentType)

  override def deleteAvatar(userId: UUID): ZIO[ApiContext, Throwable, User] =
    avatarInteractor.deleteAvatar(userId)

  def makeAdmin(userId: UUID): ZIO[ApiContext, Throwable, User] =
    persistence.makeAdmin(userId)
}
