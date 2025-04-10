package persistence.users

import api.Querying
import com.google.inject.ImplementedBy
import context.ApiContext
import domain.people.users.User
import persistence.DbPersisting
import zio.ZIO

@ImplementedBy(classOf[UsersPersistence])
trait Users extends DbPersisting[User] with Querying[User] {
  def authenticate(
      email: String,
  ): ZIO[ApiContext, Throwable, User]
}
