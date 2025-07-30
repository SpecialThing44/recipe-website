package api.users

import api.{Persisting, Querying}
import com.google.inject.ImplementedBy
import context.{ApiContext, CookingApi}
import domain.people.users.{User, UserInput, UserUpdateInput}
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
      cookingApi: CookingApi
  ): ZIO[ApiContext, Throwable, String]

  def login(
      email: String,
      password: String,
  ): ZIO[ApiContext, Throwable, Option[String]]
}
