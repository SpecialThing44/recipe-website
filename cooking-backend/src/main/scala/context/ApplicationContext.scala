package context

import domain.users.User

case class ApplicationContext(
    user: Option[User],
)

object ApplicationContext
