package http.recipes
import com.google.inject.{Inject, Singleton}
import context.CookingApi
import domain.ingredients.Ingredient
import domain.recipes.{Recipe, RecipeInput, RecipeUpdateInput}
import http.Requests
import http.Requests.extractUser
import io.circe.Decoder
import org.apache.pekko.util.ByteString
import play.api.libs.json.*
import play.api.mvc.*

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RecipesController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi
) extends AbstractController(cc) {
  import context.ApiContext
  import http.{ApiRunner, ErrorMapping}
  import io.circe.syntax.EncoderOps
  import play.api.mvc.Results
  private implicit val ec: ExecutionContext = cc.executionContext
  implicit val recipeDecoder: Decoder[Recipe] = Recipe.decoder
  implicit val ingredientDecoder: Decoder[Ingredient] = Ingredient.decoder

  def list(): Action[JsValue] = Action.async(parse.json) { request =>
    Requests
      .list[Recipe](
        request,
        cookingApi,
        cookingApi.recipes,
        authenticate = false
      )
  }

  def post(): Action[JsValue] = Action.async(parse.json) { request =>
    Requests.post[Recipe, RecipeInput, RecipeUpdateInput](
      request,
      cookingApi,
      cookingApi.recipes
    )
  }

  def get(id: java.util.UUID): Action[AnyContent] = Action.async { request =>
    Requests
      .get[Recipe](
        id,
        request,
        cookingApi,
        cookingApi.recipes,
        authenticate = false
      )(
        Recipe.encoder
      )
  }

  def put(id: java.util.UUID): Action[JsValue] = Action.async(parse.json) {
    request =>
      Requests.put[Recipe, RecipeInput, RecipeUpdateInput](
        id,
        request,
        cookingApi,
        cookingApi.recipes
      )
  }

  def delete(id: java.util.UUID): Action[AnyContent] = Action.async { request =>
    Requests.delete[Recipe](
      id,
      request,
      cookingApi,
      cookingApi.recipes
    )
  }

  def save(id: java.util.UUID): Action[AnyContent] = Action.async { request =>
    val response = cookingApi.recipes
      .save(id)
      .fold(
        error => ErrorMapping.mapCustomErrorsToHttp(error),
        saved =>
          Results.Created(
            s"{ \"Body\": ${play.api.libs.json.Json.parse(saved.asJson.noSpaces)} }"
          )
      )
    extractUser(request, cookingApi).flatMap(maybeUser =>
      ApiRunner
        .runResponseAsyncSafely[ApiContext](response, cookingApi, maybeUser)
    )
  }

  def uploadImage(id: java.util.UUID): Action[AnyContent] = Action.async {
    request =>
      extractUser(request, cookingApi).flatMap { maybeUser =>
        request.body.asRaw match {
          case Some(raw) =>
            val fileBytes = raw.asBytes().getOrElse(ByteString.empty)
            val contentType = request.contentType.getOrElse("image/jpeg")

            if (fileBytes.isEmpty) {
              Future.successful(
                BadRequest(
                  play.api.libs.json.Json
                    .obj("error" -> "No file data provided")
                )
              )
            } else {
              val response = cookingApi.recipes
                .uploadImage(id, fileBytes, contentType)
                .fold(
                  error => ErrorMapping.mapCustomErrorsToHttp(error),
                  recipe =>
                    Ok(
                      s"{ \"Body\": ${play.api.libs.json.Json.parse(recipe.asJson.noSpaces)} }"
                    )
                )
              ApiRunner.runResponseAsyncSafely(response, cookingApi, maybeUser)
            }
          case None =>
            Future.successful(
              BadRequest(
                play.api.libs.json.Json.obj("error" -> "No file uploaded")
              )
            )
        }
      }
  }

  def deleteImage(id: java.util.UUID): Action[AnyContent] = Action.async {
    request =>
      val response = cookingApi.recipes
        .deleteImage(id)
        .fold(
          error => ErrorMapping.mapCustomErrorsToHttp(error),
          recipe =>
            Ok(
              s"{ \"Body\": ${play.api.libs.json.Json.parse(recipe.asJson.noSpaces)} }"
            )
        )
      extractUser(request, cookingApi).flatMap(maybeUser =>
        ApiRunner.runResponseAsyncSafely(response, cookingApi, maybeUser)
      )
  }

  def uploadInstructionImage(id: java.util.UUID): Action[AnyContent] =
    Action.async { request =>
      extractUser(request, cookingApi).flatMap { maybeUser =>
        request.body.asRaw match {
          case Some(raw) =>
            val fileBytes = raw.asBytes().getOrElse(ByteString.empty)
            val contentType = request.contentType.getOrElse("image/jpeg")

            if (fileBytes.isEmpty) {
              Future.successful(
                BadRequest(
                  play.api.libs.json.Json
                    .obj("error" -> "No file data provided")
                )
              )
            } else {
              val response = cookingApi.recipes
                .uploadInstructionImage(
                  id,
                  fileBytes,
                  contentType
                )
                .fold(
                  error => ErrorMapping.mapCustomErrorsToHttp(error),
                  imageUrl => Ok(play.api.libs.json.Json.obj("url" -> imageUrl))
                )
              ApiRunner.runResponseAsyncSafely(response, cookingApi, maybeUser)
            }
          case None =>
            Future.successful(
              BadRequest(
                play.api.libs.json.Json.obj("error" -> "No file uploaded")
              )
            )
        }
      }
    }
}
