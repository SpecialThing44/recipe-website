package http.ingredients

import com.google.inject.{Inject, Singleton}
import context.CookingApi
import domain.ingredients.{Ingredient, IngredientInput, IngredientUpdateInput}
import http.Requests
import play.api.libs.json.*
import play.api.mvc.*

@Singleton
class IngredientsController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi
) extends AbstractController(cc) {
  def list(): Action[JsValue] = Action(parse.json) { request =>
    Requests.list[Ingredient](request, cookingApi, cookingApi.ingredients)
  }

  def post(): Action[JsValue] = Action(parse.json) { request =>
    Requests.post[Ingredient, IngredientInput, IngredientUpdateInput](
      request,
      cookingApi,
      cookingApi.ingredients
    )
  }

  def get(id: java.util.UUID): Action[AnyContent] = Action { request =>
    Requests
      .get[Ingredient](id, request, cookingApi, cookingApi.ingredients)(
        Ingredient.encoder
      )
  }

  def put(id: java.util.UUID): Action[JsValue] = Action(parse.json) { request =>
    Requests.put[Ingredient, IngredientInput, IngredientUpdateInput](
      id,
      request,
      cookingApi,
      cookingApi.ingredients
    )
  }

  def delete(id: java.util.UUID): Action[AnyContent] = Action { request =>
    Requests
      .delete[Ingredient](id, request, cookingApi, cookingApi.ingredients)(
        Ingredient.encoder
      )
  }
}
