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

@Singleton
class RecipesController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi
) extends AbstractController(cc) {
  import context.ApiContext
  import http.{ApiRunner, ErrorMapping}
  import io.circe.syntax.EncoderOps
  import play.api.mvc.Results
  implicit val recipeDecoder: Decoder[Recipe] = Recipe.decoder
  implicit val ingredientDecoder: Decoder[Ingredient] = Ingredient.decoder

  def list(): Action[JsValue] = Action(parse.json) { request =>
    Requests.list[Recipe](request, cookingApi, cookingApi.recipes)
  }

  def post(): Action[JsValue] = Action(parse.json) { request =>
    Requests.post[Recipe, RecipeInput, RecipeUpdateInput](
      request,
      cookingApi,
      cookingApi.recipes
    )
  }

  def get(id: java.util.UUID): Action[AnyContent] = Action { request =>
    Requests.get[Recipe](id, request, cookingApi, cookingApi.recipes)(
      Recipe.encoder
    )
  }

  def put(id: java.util.UUID): Action[JsValue] = Action(parse.json) { request =>
    Requests.put[Recipe, RecipeInput, RecipeUpdateInput](
      id,
      request,
      cookingApi,
      cookingApi.recipes
    )
  }

  def save(id: java.util.UUID): Action[AnyContent] = Action { request =>
    val maybeUser = extractUser(request, cookingApi)
    val result = cookingApi.recipes.save(id)
    val response = result.fold(
      error => ErrorMapping.mapCustomErrorsToHttp(error),
      saved =>
        Results.Created(
          s"{ \"Body\": ${play.api.libs.json.Json.parse(saved.asJson.noSpaces)} }"
        )
    )
    ApiRunner.runResponseSafely[ApiContext](response, cookingApi, maybeUser)
  }

  def uploadImage(id: java.util.UUID): Action[AnyContent] = Action { request =>
    val maybeUser = extractUser(request, cookingApi)

    request.body.asRaw match {
      case Some(raw) =>
        val fileBytes = raw.asBytes().getOrElse(ByteString.empty)
        val contentType = request.contentType.getOrElse("image/jpeg")

        if (fileBytes.isEmpty) {
          BadRequest(
            play.api.libs.json.Json.obj("error" -> "No file data provided")
          )
        } else {
          val result =
            cookingApi.recipes.uploadImage(id, fileBytes, contentType)
          val response = result.fold(
            error => ErrorMapping.mapCustomErrorsToHttp(error),
            recipe =>
              Ok(
                s"{ \"Body\": ${play.api.libs.json.Json.parse(recipe.asJson.noSpaces)} }"
              )
          )
          ApiRunner.runResponseSafely(response, cookingApi, maybeUser)
        }
      case None =>
        BadRequest(play.api.libs.json.Json.obj("error" -> "No file uploaded"))
    }
  }

  def deleteImage(id: java.util.UUID): Action[AnyContent] = Action { request =>
    val maybeUser = extractUser(request, cookingApi)
    val result = cookingApi.recipes.deleteImage(id)
    val response = result.fold(
      error => ErrorMapping.mapCustomErrorsToHttp(error),
      recipe =>
        Ok(
          s"{ \"Body\": ${play.api.libs.json.Json.parse(recipe.asJson.noSpaces)} }"
        )
    )
    ApiRunner.runResponseSafely(response, cookingApi, maybeUser)
  }

  def uploadInstructionImage(id: java.util.UUID): Action[AnyContent] = Action {
    request =>
      val maybeUser = extractUser(request, cookingApi)

      request.body.asRaw match {
        case Some(raw) =>
          val fileBytes = raw.asBytes().getOrElse(ByteString.empty)
          val contentType = request.contentType.getOrElse("image/jpeg")

          if (fileBytes.isEmpty) {
            BadRequest(
              play.api.libs.json.Json.obj("error" -> "No file data provided")
            )
          } else {
            val result = cookingApi.recipes.uploadInstructionImage(
              id,
              fileBytes,
              contentType
            )
            val response = result.fold(
              error => ErrorMapping.mapCustomErrorsToHttp(error),
              imageUrl => Ok(play.api.libs.json.Json.obj("url" -> imageUrl))
            )
            ApiRunner.runResponseSafely(response, cookingApi, maybeUser)
          }
        case None =>
          BadRequest(play.api.libs.json.Json.obj("error" -> "No file uploaded"))
      }
  }
}
