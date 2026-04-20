package api.ai

import api.ingredients.IngredientsApi
import api.tags.TagsApi
import com.google.inject.Inject
import context.ApiContext
import domain.ai.AiRecipeParseResponse
import domain.filters.Filters
import zio.ZIO

class AiFacade @Inject() (
    aiInteractor: AiInteractor,
    ingredientsApi: IngredientsApi,
    tagsApi: TagsApi
) {
  def pingOllama(): ZIO[ApiContext, Throwable, Unit] =
    aiInteractor.pingOllama()

  def parseRecipe(text: String): ZIO[ApiContext, Throwable, AiRecipeParseResponse] =
    for {
      ingredients <- ingredientsApi.list(Filters.empty())
      tags <- tagsApi.list(Filters.empty())
      result <- aiInteractor.parseRecipe(text, ingredients, tags)
    } yield result
}
