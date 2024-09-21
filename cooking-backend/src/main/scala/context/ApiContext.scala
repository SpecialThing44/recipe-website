package context

final case class ApiContext(
    api: CookingApi,
    applicationContext: ApplicationContext
)

object ApiContext {}
