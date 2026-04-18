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
  private val recipePromptTemplate = config.getOptional[String]("ollama.recipePromptTemplate").getOrElse(
    """You are a culinary AI turning unstructured text into a structured recipe.
Output MUST be raw JSON only.
Do not output explanations, analysis, or thinking.
Do not output markdown.
You MUST follow the schema exactly.

Allowed Ingredients List (name, aliases, and id):
%KNOWN_INGREDIENTS%
Allowed Tags List: %KNOWN_TAGS%
Allowed Units List: %KNOWN_UNITS%

Rules for Ingredients:
1. If an ingredient matches one in the "Allowed Ingredients List" (exact or alias), set `ingredientId` to that ingredient id and `ingredientName` to the canonical allowed ingredient name.
2. If an ingredient is NOT in the list, set `ingredientId` to null and `ingredientName` to null.
3. `ingredientId` and `ingredientName` must each be either a single string or null. Never return arrays/lists for these fields.
4. Always provide `quantity` as an object with numeric `amount` and `unit` from the allowed units list.
5. If no clear unit is present, use unit `piece`.
6. For unknown ingredients, explicitly mention them at the end of the `instructions` field. If there are no unknown ingredients, do not mention anything.

Rules for Tags:
1. Only use tags exactly as they appear in the "Allowed Tags List". Skip any tags that don't match. Choose included tags that make sense based on the description of the recipe and its ingredients.

Expected JSON Schema:
{
  "name": "Recipe Title",
  "instructions": "Step 1...\\nStep 2...\\n\\nNote: Missing ingredients: unobtainium",
  "prepTime": 15,
  "cookTime": 30,
  "servings": 4,
  "tags": ["tag1", "tag2"],
  "ingredients": [
    {
      "rawText": "1 cup diced tomatoes",
      "ingredientId": "550e8400-e29b-41d4-a716-446655440000",
      "ingredientName": "tomato",
      "quantity": {
        "amount": 1,
        "unit": "cup"
      },
      "description": null
    },
    {
      "rawText": "2 tbsp unobtainium extract",
      "ingredientId": null,
      "ingredientName": null,
      "quantity": {
        "amount": 2,
        "unit": "tablespoon"
      },
      "description": "unobtainium extract"
    }
  ]
}

Recipe text to parse:
%RECIPE_TEXT%"""
  )
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
