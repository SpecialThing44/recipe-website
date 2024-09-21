package api.users

import com.google.inject.Inject
import context.ApiContext
import domain.people.users.User
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import persistence.recipes.Users
import play.api.libs.json.JsValue
import zio.ZIO

import java.util.UUID

class UserFacade @Inject() (
    val persistence: Users
) extends UserApi {

  override def create(
      entity: User
  ): ZIO[ApiContext, Throwable, User] = ???

  override def update(
      entity: User,
      originalEntity: User
  ): ZIO[ApiContext, Throwable, User] = ???

  override def list(
      query: JsValue
  ): ZIO[ApiContext, Throwable, Seq[User]] = ???

  override def find(query: JsValue): ZIO[ApiContext, Throwable, User] =
    ???

  override def get(id: UUID): ZIO[ApiContext, Throwable, User] = ???

  override def authenticate(
      bearerToken: OAuth2BearerToken
  ): ZIO[ApiContext, Throwable, Option[User]] = ???

}
