package api.users

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.users.User
import persistence.users.Users
import zio.ZIO

class UserGetInteractor @Inject() (
    persistence: Users,
) {
  def getById(id: java.util.UUID): ZIO[ApiContext, Throwable, User] = for {
    context <- ZIO.service[ApiContext]
    user <- persistence.getById(id)
    safeToViewUser <- ZIO.succeed(if (context.applicationContext.user.fold(false)(loggedInUser => loggedInUser.id == user.id)) user else user.copy(email = ""))
  } yield safeToViewUser

}
