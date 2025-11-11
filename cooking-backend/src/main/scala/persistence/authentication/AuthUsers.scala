package persistence.authentication

import com.google.inject.ImplementedBy
import context.ApiContext
import domain.users.User
import persistence.{DbPersisting, DbQuerying}
import zio.ZIO

@ImplementedBy(classOf[AuthUsersPersistence])
trait AuthUsers extends DbPersisting[AuthUser] with DbQuerying[AuthUser] {
  def authenticate(
      email: String,
  ): ZIO[ApiContext, Throwable, AuthUser]

}
