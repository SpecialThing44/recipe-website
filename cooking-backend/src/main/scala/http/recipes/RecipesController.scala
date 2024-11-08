package http.recipes
import com.google.inject.{Inject, Singleton}
import context.CookingApi
import domain.food.ingredients.Ingredient
import domain.food.recipes.Recipe
import http.Requests
import io.circe.Decoder
import play.api.libs.json.*
import play.api.mvc.*

@Singleton
class RecipesController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi
) extends AbstractController(cc) {
  implicit val recipeDecoder: Decoder[Recipe] = Recipe.decoder
  implicit val ingredientDecoder: Decoder[Ingredient] = Ingredient.decoder

  def list(): Action[JsValue] = Action(parse.json) { request =>
    Requests.list[Recipe](request, cookingApi, cookingApi.recipes)
  }

  def post(): Action[JsValue] = Action(parse.json) { request =>
    Requests.post[Recipe](request, cookingApi, cookingApi.recipes)
  }

  def get(id: java.util.UUID): Action[JsValue] = Action(parse.json) { request =>
    Requests.get[Recipe](id, request, cookingApi, cookingApi.recipes)
  }

  def put(id: java.util.UUID): Action[JsValue] = Action(parse.json) { request =>
    Requests.put[Recipe](id, request, cookingApi, cookingApi.recipes)
  }
}
