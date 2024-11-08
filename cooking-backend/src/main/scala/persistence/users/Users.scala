package persistence.users

import api.{Persisting, Querying}
import com.google.inject.ImplementedBy
import domain.people.users.User

@ImplementedBy(classOf[UsersPersistence])
trait Users extends Persisting[User] with Querying[User]
