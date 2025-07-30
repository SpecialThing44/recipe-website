package api.users

import com.google.inject.Inject
import context.{ApiContext, CookingApi}
import domain.people.users.{User, UserInput, UserUpdateInput}
import domain.types.Fault
import persistence.users.Users
import play.api.libs.json.JsValue
import play.api.mvc.Request
import zio.ZIO

import java.util.UUID

class UserFacade @Inject() (
    persistence: Users,
    authenticationInteractor: AuthenticationInteractor,
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
      _ <- authenticationInteractor.ensureAuthenticated(
        context.applicationContext.user,
        originalEntity.id
      )
      updatedUser <- persistence.update(
        UserAdapter.adaptUpdate(entity, originalEntity),
        originalEntity
      )
    } yield updatedUser

  override def delete(id: UUID): ZIO[ApiContext, Throwable, User] = ???

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
    println("Logout request")
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
