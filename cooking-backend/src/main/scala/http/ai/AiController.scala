package http.ai

import api.ai.AiFacade
import com.google.inject.{Inject, Singleton}
import context.{ApiContext, CookingApi}
import domain.ai.AiParseParams
import http.{ApiRunner, Requests}
import play.api.libs.json.Json
import play.api.mvc.*
import zio.ZIO

import scala.concurrent.ExecutionContext

@Singleton
class AiController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi,
    aiFacade: AiFacade
) extends AbstractController(cc) {
  private implicit val ec: ExecutionContext = cc.executionContext

  def check(): Action[AnyContent] = Action.async { _ =>
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

    ApiRunner.runResponseAsyncSafely(response, cookingApi, None)
  }

  def parseRecipe(): Action[play.api.libs.json.JsValue] =
    Action.async(parse.json) { request =>
      import io.circe.syntax.*

      val parseZio: ZIO[ApiContext, Throwable, Result] = for {
        params <- ZIO.fromEither(
          io.circe.parser.decode[AiParseParams](request.body.toString())
        )
        res <- aiFacade.parseRecipe(params.text)
      } yield play.api.mvc.Results
        .Ok(res.asJson.noSpaces)
        .as("application/json")

      Requests
        .extractUser(request, cookingApi)
        .flatMap(user =>
          ApiRunner.runResponseAsyncSafely(
            parseZio,
            cookingApi,
            user
          )
        )
    }
}
