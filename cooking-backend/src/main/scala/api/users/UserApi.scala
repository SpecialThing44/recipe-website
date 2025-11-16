package api.users

import api.{Persisting, Querying}
import com.google.inject.ImplementedBy
import context.{ApiContext, CookingApi}
import domain.authentication.TokenPair
import domain.users.{User, UserInput, UserUpdateInput}
import play.api.mvc.Request
import zio.ZIO

@ImplementedBy(classOf[UserFacade])
trait UserApi
    extends Persisting[User, UserInput, UserUpdateInput]
    with Querying[User] {
  def authenticate(
      bearerToken: Option[String]
  ): ZIO[ApiContext, Throwable, Option[User]]

  def logout(
      request: Request[?],
  ): ZIO[ApiContext, Throwable, Boolean]

  def signup(
      user: UserInput,
  ): ZIO[ApiContext, Throwable, TokenPair]

  def login(
      email: String,
      password: String,
  ): ZIO[ApiContext, Throwable, Option[TokenPair]]

  def refresh(
      refreshToken: String
  ): ZIO[ApiContext, Throwable, Option[TokenPair]]

  def deleteAll(): ZIO[ApiContext, Throwable, Unit]
}
