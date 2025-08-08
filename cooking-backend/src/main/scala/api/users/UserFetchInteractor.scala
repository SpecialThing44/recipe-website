package api.users

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.users.User
import persistence.users.Users
import zio.ZIO

class UserFetchInteractor @Inject() (
    persistence: Users,
) {
  def list(query: Filters): ZIO[ApiContext, Throwable, Seq[User]] =
    persistence.list(query)
}
