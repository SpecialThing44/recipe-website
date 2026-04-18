package http.ai

import com.google.inject.{Inject, Singleton}
import context.{ApiContext, CookingApi}
import domain.ai.AiParseParams
import domain.filters.Filters
import http.{ApiRunner, Requests}
import play.api.libs.json.Json
import play.api.mvc.*
import services.ai.AiService
import zio.ZIO

@Singleton
class AiController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi,
    aiService: AiService
) extends AbstractController(cc) {

  def check(): Action[AnyContent] = Action { _ =>
    val response: ZIO[ApiContext, Throwable, Result] =
      aiService.pingOllama().as(
        Ok(
          Json.obj(
            "status" -> "ok",
            "service" -> "ollama"
          )
        )
      )

    ApiRunner.runResponseSafely(response, cookingApi, None)
  }

  def parseRecipe(): Action[play.api.libs.json.JsValue] = Action(parse.json) {
    request =>
      val user = Requests.extractUser(request, cookingApi)

      val bodyString = request.body.toString()

      import io.circe.syntax._

      val parseZio: ZIO[ApiContext, Throwable, Result] = for {
        params <- ZIO.fromEither(io.circe.parser.decode[AiParseParams](bodyString))
        ingredients <- cookingApi.ingredients.list(Filters.empty())
        tags <- cookingApi.tags.list(Filters.empty())
        res <- aiService.parseRecipe(params.text, ingredients, tags)
      } yield play.api.mvc.Results.Ok(res.asJson.noSpaces).as("application/json")

      ApiRunner.runResponseSafely(
        parseZio,
        cookingApi,
        user
      )
  }
}