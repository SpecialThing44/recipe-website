package api.users

import com.google.inject.Inject
import context.{ApiContext, CookingApi}
import domain.filters.Filters
import domain.users.{User, UserInput, UserUpdateInput}
import persistence.users.Users
import play.api.libs.json.JsValue
import play.api.mvc.Request
import zio.ZIO

import java.util.UUID

class UserFacade @Inject() (
    persistence: Users,
    authenticationInteractor: AuthenticationInteractor,
    deleteInteractor: UserDeleteInteractor,
    updateInteractor: UserUpdateInteractor,
    fetchInteractor: UserFetchInteractor
) extends UserApi {

  override def create(
      entity: UserInput
  ): ZIO[ApiContext, Throwable, User] =
    persistence.create(UserAdapter.adapt(entity))

  override def update(
      entity: UserUpdateInput,
      originalEntity: User
  ): ZIO[ApiContext, Throwable, User] =
    updateInteractor.update(entity, originalEntity)

  override def delete(id: UUID): ZIO[ApiContext, Throwable, User] =
    deleteInteractor.delete(id)

  override def list(query: Filters): ZIO[ApiContext, Throwable, Seq[User]] = fetchInteractor.list(query)

  override def getById(id: UUID): ZIO[ApiContext, Throwable, User] =
    persistence.getById(id)

  def getByIdWithPassword(id: UUID): ZIO[ApiContext, Throwable, User] =
    persistence.getByIdWithPassword(id)

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
