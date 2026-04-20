package services.ai

import com.google.inject.{Inject, Singleton}
import domain.ai.AiRecipeParseResponse
import domain.ingredients.{Ingredient, Unit as IngredientUnit}
import domain.types.SystemError
import io.circe.Json
import io.circe.parser._
import play.api.Configuration
import sttp.client3._
import zio.ZIO

import scala.concurrent.duration.*
import scala.util.control.NonFatal

@Singleton
class AiService @Inject() (config: Configuration) {
  private val backend = HttpClientSyncBackend()
  private val ollamaUrl = config.getOptional[String]("ollama.url").getOrElse("http://localhost:11434")
  private val model = config.getOptional[String]("ollama.model").getOrElse("qwen2.5:14b")
  private val chatTimeoutSeconds = config.getOptional[Int]("ollama.chatTimeoutSeconds").getOrElse(180)
  private val recipePromptTemplate = config.get[String]("ollama.recipePromptTemplate")
  private val chatTimeout = chatTimeoutSeconds.seconds

  private def ollamaConnectionError(action: String, cause: String): SystemError = {
    SystemError(
      s"Ollama $action failed for URL '$ollamaUrl': $cause. " +
        "For local testing with sbt runBackend, set OLLAMA_URL to your Ollama host, " +
        "for example: OLLAMA_URL=http://192.168.2.16:11434 sbt runBackend"
    )
  }

  private def parseUrl(path: String, action: String): sttp.model.Uri = {
    sttp.model.Uri.parse(s"$ollamaUrl$path") match {
      case Right(u) => u
      case Left(e) => throw ollamaConnectionError(action, s"invalid URL: $e")
    }
  }

  private def sendOrThrow(
      request: Request[Either[String, String], Any],
      action: String
  ): Response[Either[String, String]] = {
    try {
      request.send(backend)
    } catch {
      case NonFatal(e) =>
        throw ollamaConnectionError(action, s"transport error: ${e.getClass.getSimpleName}: ${e.getMessage}")
    }
  }

  def pingOllama(): ZIO[Any, Throwable, Unit] = ZIO.attemptBlocking {
    val parsedUrl = parseUrl("/api/tags", "health check")

    val response = sendOrThrow(
      basicRequest
      .get(parsedUrl)
      .readTimeout(10.seconds),
      "health check"
    )

    response.body match {
      case Right(body) =>
        val modelExists = parse(body)
          .flatMap(_.hcursor.downField("models").as[Seq[Json]])
          .toOption
          .exists(models =>
            models.exists(modelJson =>
              modelJson.hcursor.get[String]("name").toOption.exists(_.startsWith(model))
            )
          )

        if !modelExists then {
          throw SystemError(
            s"Ollama is reachable at '$ollamaUrl' but model '$model' is not available. " +
              "Run: ollama pull " + model
          )
        }
      case Left(err) => throw ollamaConnectionError("health check", err)
    }
  }

  def parseRecipe(text: String, knownIngredients: Seq[Ingredient], knownTags: Seq[String]): ZIO[Any, Throwable, AiRecipeParseResponse] = ZIO.attemptBlocking {
    val knownIngredientsPrompt = knownIngredients
      .map(i => s"- id: ${i.id}, name: ${i.name}, aliases: [${i.aliases.mkString(", ")}]" )
      .mkString("\n")
    val knownUnitsPrompt = IngredientUnit.values.map(_.name).mkString(", ")

    val prompt = recipePromptTemplate
      .replace("%KNOWN_INGREDIENTS%", knownIngredientsPrompt)
      .replace("%KNOWN_TAGS%", knownTags.mkString(", "))
      .replace("%KNOWN_UNITS%", knownUnitsPrompt)
      .replace("%RECIPE_TEXT%", text)

    val payload = Json.obj(
      "model" -> Json.fromString(model),
      "messages" -> Json.arr(
        Json.obj(
          "role" -> Json.fromString("user"),
          "content" -> Json.fromString(prompt)
        )
      ),
      "stream" -> Json.fromBoolean(false),
      "format" -> Json.fromString("json"),
      "think" -> Json.fromBoolean(false),
      "options" -> Json.obj(
        "temperature" -> Json.fromDoubleOrNull(0.1),
        "num_predict" -> Json.fromInt(1600)
      )
    )

    val parsedUrl = parseUrl("/api/chat", "chat request")

    val request = basicRequest
      .post(parsedUrl)
      .header("Content-Type", "application/json")
      .readTimeout(chatTimeout)
      .body(payload.noSpaces)

    val response = sendOrThrow(request, "chat request")

    response.body match {
      case Right(body) =>
        val parseResult = for {
          parsedBody <- parse(body)
          content <- parsedBody.hcursor.downField("message").downField("content").as[String]
          parsedResponse <- parse(content).flatMap(_.as[AiRecipeParseResponse])
        } yield parsedResponse

        parseResult match {
          case Right(res) =>
            AiResponseValidator.validateParsedRecipe(res, knownIngredients) match {
              case Right(validated) => validated
              case Left(error) => throw new RuntimeException(s"Malformed AI parse response: $error")
            }
          case Left(err) =>
            throw new RuntimeException(s"Failed to parse LLM response: $err. Body: $body")
        }
      case Left(err) =>
        throw ollamaConnectionError("chat request", err)
    }
  }
}
