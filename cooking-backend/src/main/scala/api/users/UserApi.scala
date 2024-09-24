package api.users

import api.Querying
import context.ApiContext
import domain.people.users.User
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import persistence.Persisting
import zio.ZIO

trait UserApi extends Persisting[User] with Querying[User] {
  def authenticate(
      bearerToken: OAuth2BearerToken
  ): ZIO[ApiContext, Throwable, Option[User]]
}
