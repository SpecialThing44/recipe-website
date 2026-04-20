package http.ai

import api.ai.AiFacade
import com.google.inject.{Inject, Singleton}
import context.{ApiContext, CookingApi}
import domain.ai.AiParseParams
import http.{ApiRunner, Requests}
import play.api.libs.json.Json
import play.api.mvc.*
import zio.ZIO

@Singleton
class AiController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi,
  aiFacade: AiFacade
) extends AbstractController(cc) {

  def check(): Action[AnyContent] = Action { _ =>
    val response: ZIO[ApiContext, Throwable, Result] =
      aiFacade
        .pingOllama()
        .as(
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
      import io.circe.syntax.*

      val parseZio: ZIO[ApiContext, Throwable, Result] = for {
        params <- ZIO.fromEither(
          io.circe.parser.decode[AiParseParams](request.body.toString())
        )
        res <- aiFacade.parseRecipe(params.text)
      } yield play.api.mvc.Results
        .Ok(res.asJson.noSpaces)
        .as("application/json")

      ApiRunner.runResponseSafely(
        parseZio,
        cookingApi,
        user
      )
  }
}
