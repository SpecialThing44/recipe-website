package http.ingredients

import com.google.inject.{Inject, Singleton}
import context.CookingApi
import domain.food.ingredients.Ingredient
import http.Requests
import io.circe.Decoder
import play.api.libs.json._
import play.api.mvc._

@Singleton
class IngredientsController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi
) extends AbstractController(cc) {
  implicit val ingredientDecoder: Decoder[Ingredient] = Ingredient.decoder

  def list(): Action[JsValue] = Action(parse.json) { request =>
    Requests.list[Ingredient](request, cookingApi, cookingApi.ingredients)
  }

  def post(): Action[JsValue] = Action(parse.json) { request =>
    Requests.post[Ingredient](request, cookingApi, cookingApi.ingredients)
  }

  def get(id: java.util.UUID): Action[JsValue] = Action(parse.json) { request =>
    Requests
      .get[Ingredient](id, request, cookingApi, cookingApi.ingredients)
  }

  def put(id: java.util.UUID): Action[JsValue] = Action(parse.json) { request =>
    Requests.put[Ingredient](id, request, cookingApi, cookingApi.ingredients)
  }
}
