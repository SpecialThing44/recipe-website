package http.recipes
import com.google.inject.{Inject, Singleton}
import context.CookingApi
import domain.ingredients.Ingredient
import domain.recipes.{Recipe, RecipeInput, RecipeUpdateInput}
import http.Requests
import io.circe.Decoder
import play.api.libs.json.*
import play.api.mvc.*

@Singleton
class RecipesController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi
) extends AbstractController(cc) {
  import io.circe.syntax.EncoderOps
  import domain.filters.Filters
  import http.ApiRunner
  import http.ErrorMapping
  import play.api.mvc.Results
  import context.ApiContext
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
    val maybeUser = request.headers.get("Authorization")
    val result = cookingApi.recipes.save(id)
    val response = result.fold(
      error => ErrorMapping.mapCustomErrorsToHttp(error),
      saved => Results.Created(s"{ \"Body\": ${play.api.libs.json.Json.parse(saved.asJson.noSpaces)} }")
    )
    ApiRunner.runResponseSafely[ApiContext](response, cookingApi, None)
  }
}
