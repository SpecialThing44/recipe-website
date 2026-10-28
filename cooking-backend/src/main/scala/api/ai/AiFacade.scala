package api.ai

import api.users.AuthenticationInteractor
import domain.types.{InputError, ServiceBusyError}
import api.ingredients.IngredientsApi
import api.tags.TagsApi
import com.google.inject.Inject
import context.ApiContext
import domain.ai.AiRecipeParseResponse
import domain.filters.Filters
import zio.ZIO

@com.google.inject.Singleton
class AiFacade @Inject() (
    aiInteractor: AiInteractor,
    ingredientsApi: IngredientsApi,
    tagsApi: TagsApi
) {
  private val parseSlots = new java.util.concurrent.Semaphore(2)

  def pingOllama(): ZIO[ApiContext, Throwable, Unit] =
    aiInteractor.pingOllama()

  def parseRecipe(
      text: String
  ): ZIO[ApiContext, Throwable, AiRecipeParseResponse] =
    for {
      context <- ZIO.service[ApiContext]
      _ <- AuthenticationInteractor.ensureIsLoggedIn(
        context.applicationContext.user
      )
      _ <- ZIO
        .fail(InputError("Recipe text must contain 1 to 20000 characters"))
        .when(text.trim.isEmpty || text.length > 20000)
      result <- ZIO.acquireReleaseWith(
        ZIO
          .attempt(parseSlots.tryAcquire())
          .flatMap(acquired =>
            if (acquired) ZIO.unit
            else
              ZIO
                .fail(ServiceBusyError("AI parsing is busy; try again shortly"))
          )
      )(_ => ZIO.succeed(parseSlots.release())) { _ =>
        for {
          ingredients <- ingredientsApi.list(Filters.empty())
          tags <- tagsApi.list(Filters.empty())
          parsed <- aiInteractor.parseRecipe(text, ingredients, tags)
        } yield parsed
      }
    } yield result
}
