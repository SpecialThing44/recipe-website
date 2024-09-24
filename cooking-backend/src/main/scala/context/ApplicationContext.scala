package context

import domain.people.users.User

case class ApplicationContext(
    user: Option[User],
)

object ApplicationContext
