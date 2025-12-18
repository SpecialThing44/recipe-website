package api.users

import api.{Persisting, Querying}
import com.google.inject.ImplementedBy
import context.ApiContext
import domain.users.{User, UserInput, UserUpdateInput}
import org.apache.pekko.util.ByteString
import zio.ZIO

import java.util.UUID

@ImplementedBy(classOf[UserFacade])
trait UserApi
    extends Persisting[User, UserInput, UserUpdateInput]
    with Querying[User] {
  def authenticate(
      bearerToken: Option[String]
  ): ZIO[ApiContext, Throwable, Option[User]]

  def deleteAll(): ZIO[ApiContext, Throwable, Unit]

  def uploadAvatar(
      userId: UUID,
      fileBytes: ByteString,
      contentType: String
  ): ZIO[ApiContext, Throwable, User]

  def deleteAvatar(userId: UUID): ZIO[ApiContext, Throwable, User]
}
