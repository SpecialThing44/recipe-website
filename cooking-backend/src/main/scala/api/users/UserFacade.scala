package api.users

import domain.authentication.TokenPair
import com.google.inject.Inject
import context.{ApiContext, CookingApi}
import domain.filters.Filters
import domain.users.{User, UserInput, UserUpdateInput}
import org.apache.pekko.util.ByteString
import persistence.users.Users
import play.api.mvc.Request
import zio.ZIO

import java.util.UUID

class UserFacade @Inject() (
    persistence: Users,
    authenticationInteractor: AuthenticationInteractor,
    deleteInteractor: UserDeleteInteractor,
    updateInteractor: UserUpdateInteractor,
    fetchInteractor: UserFetchInteractor,
    avatarInteractor: UserAvatarInteractor
) extends UserApi {

  override def create(
      entity: UserInput
  ): ZIO[ApiContext, Throwable, User] =
    authenticationInteractor.signup(entity)

  override def update(
      entity: UserUpdateInput,
      originalEntity: User
  ): ZIO[ApiContext, Throwable, User] =
    updateInteractor.update(entity, originalEntity)

  override def delete(id: UUID): ZIO[ApiContext, Throwable, User] =
    deleteInteractor.delete(id)
    
  override def deleteAll(): ZIO[ApiContext, Throwable, Unit] = deleteInteractor.deleteAll()

  override def list(filters: Filters): ZIO[ApiContext, Throwable, Seq[User]] =
    fetchInteractor.list(filters)

  override def getById(id: UUID): ZIO[ApiContext, Throwable, User] =
    persistence.getById(id)

  override def authenticate(
      bearerToken: Option[String]
  ): ZIO[ApiContext, Throwable, Option[User]] =
    authenticationInteractor.getMaybeUser(bearerToken)

  override def logout(
      request: Request[?]
  ): ZIO[ApiContext, Throwable, Boolean] = {
    authenticationInteractor.logout(request)
  }

  override def login(
      email: String,
      password: String,
  ): ZIO[ApiContext, Throwable, Option[TokenPair]] =
    authenticationInteractor.login(email, password)

  override def signup(
      user: UserInput,
  ): ZIO[ApiContext, Throwable, TokenPair] =
    authenticationInteractor.signupAndLogin(user)

  override def refresh(
      refreshToken: String
  ): ZIO[ApiContext, Throwable, Option[TokenPair]] =
    authenticationInteractor.refreshAccessToken(refreshToken)

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
