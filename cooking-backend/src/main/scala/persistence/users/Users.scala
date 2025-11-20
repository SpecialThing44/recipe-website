package persistence.users

import com.google.inject.ImplementedBy
import context.ApiContext
import domain.users.User
import persistence.{DbPersisting, DbQuerying}
import zio.ZIO

@ImplementedBy(classOf[UsersPersistence])
trait Users extends DbPersisting[User] with DbQuerying[User] {
  def deleteAll(): ZIO[ApiContext, Throwable, Unit]
}
