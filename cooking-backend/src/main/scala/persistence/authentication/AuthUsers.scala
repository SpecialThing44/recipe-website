package persistence.authentication

import com.google.inject.ImplementedBy
import context.ApiContext
import domain.authentication.AuthUser
import persistence.{DbPersisting, DbQuerying}
import zio.ZIO

@ImplementedBy(classOf[AuthUsersPersistence])
trait AuthUsers extends DbPersisting[AuthUser] with DbQuerying[AuthUser] {}
