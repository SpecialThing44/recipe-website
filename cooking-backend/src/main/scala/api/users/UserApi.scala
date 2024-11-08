package api.users

import api.{Persisting, Querying}
import com.google.inject.ImplementedBy
import context.ApiContext
import domain.people.users.User
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import zio.ZIO

@ImplementedBy(classOf[UserFacade])
trait UserApi extends Persisting[User] with Querying[User] {
  def authenticate(
      bearerToken: OAuth2BearerToken
  ): ZIO[ApiContext, Throwable, Option[User]]
}
