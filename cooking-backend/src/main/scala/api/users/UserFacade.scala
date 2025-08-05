package api.users

import com.google.inject.Inject
import context.{ApiContext, CookingApi}
import domain.people.users.{User, UserInput, UserUpdateInput}
import persistence.users.Users
import play.api.libs.json.JsValue
import play.api.mvc.Request
import zio.ZIO

import java.util.UUID

class UserFacade @Inject() (
    persistence: Users,
    authenticationInteractor: AuthenticationInteractor,
    deleteInteractor: DeleteInteractor[User]
) extends UserApi {

  override def create(
      entity: UserInput
  ): ZIO[ApiContext, Throwable, User] =
    persistence.create(UserAdapter.adapt(entity))

  override def update(
      entity: UserUpdateInput,
      originalEntity: User
  ): ZIO[ApiContext, Throwable, User] =
    for {
      context <- ZIO.service[ApiContext]
      _ <- authenticationInteractor.ensureAuthenticatedAndMatchingUser(
        context.applicationContext.user,
        originalEntity.id
      )
      updatedUser <- persistence.update(
        UserAdapter.adaptUpdate(entity, originalEntity),
        originalEntity
      )
    } yield updatedUser

  override def delete(id: UUID): ZIO[ApiContext, Throwable, User] = 
    for {
      context <- ZIO.service[ApiContext]
      user <- persistence.getById(id)
      _ <- authenticationInteractor.ensureAuthenticatedAndMatchingUser(
        context.applicationContext.user,
        user.id
      )
      deletedUser <- persistence.delete(id)
    } yield deletedUser

  override def list(
      query: JsValue
  ): ZIO[ApiContext, Throwable, Seq[User]] = ???

  override def find(query: JsValue): ZIO[ApiContext, Throwable, User] =
    ???

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
  ): ZIO[ApiContext, Throwable, Option[String]] =
    authenticationInteractor.login(email, password)

  override def signup(
      user: UserInput,
      cookingApi: CookingApi
  ): ZIO[ApiContext, Throwable, String] =
    authenticationInteractor.signup(UserAdapter.adapt(user))
}
