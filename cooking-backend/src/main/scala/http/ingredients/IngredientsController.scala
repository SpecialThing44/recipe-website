package http.ingredients

import com.google.inject.{Inject, Singleton}
import context.CookingApi
import domain.ingredients.{Ingredient, IngredientInput, IngredientUpdateInput}
import http.{ApiRunner, ErrorMapping, Requests}
import io.circe.syntax.EncoderOps
import play.api.libs.json.*
import play.api.mvc.*
import play.api.mvc.Results.{Created, Ok}

import java.util.UUID
import scala.concurrent.ExecutionContext

@Singleton
class IngredientsController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi
) extends AbstractController(cc) {
  private implicit val ec: ExecutionContext = cc.executionContext

  def list(): Action[JsValue] = Action.async(parse.json) { request =>
    Requests
      .list[Ingredient](
        request,
        cookingApi,
        cookingApi.ingredients,
        authenticate = false
      )
  }

  def post(): Action[JsValue] = Action.async(parse.json) { request =>
    Requests.post[Ingredient, IngredientInput, IngredientUpdateInput](
      request,
      cookingApi,
      cookingApi.ingredients
    )
  }

  def get(id: java.util.UUID): Action[AnyContent] = Action.async { request =>
    Requests
      .get[Ingredient](
        id,
        request,
        cookingApi,
        cookingApi.ingredients,
        authenticate = false
      )(
        Ingredient.encoder
      )
  }

  def put(id: java.util.UUID): Action[JsValue] = Action.async(parse.json) {
    request =>
      Requests.put[Ingredient, IngredientInput, IngredientUpdateInput](
        id,
        request,
        cookingApi,
        cookingApi.ingredients
      )
  }

  def delete(id: java.util.UUID): Action[AnyContent] = Action.async { request =>
    Requests
      .delete[Ingredient](id, request, cookingApi, cookingApi.ingredients)(
        Ingredient.encoder
      )
  }

  def substitutes(id: UUID): Action[AnyContent] = Action.async { request =>
    val response = cookingApi.ingredients
      .listSubstitutes(id)
      .fold(
        error => ErrorMapping.mapCustomErrorsToHttp(error),
        result => Ok(s"{ \"Body\": ${Json.parse(result.asJson.noSpaces)}}")
      )
    Requests
      .extractUser(request, cookingApi)
      .flatMap(maybeUser =>
        ApiRunner.runResponseAsyncSafely(response, cookingApi, maybeUser)
      )
  }

  def addSubstitute(
      id: UUID,
      substituteId: UUID
  ): Action[AnyContent] = Action.async { request =>
    val response = cookingApi.ingredients
      .addSubstitute(id, substituteId)
      .fold(
        error => ErrorMapping.mapCustomErrorsToHttp(error),
        _ => Created(Json.obj("message" -> "Substitute relationship created"))
      )
    Requests
      .extractUser(request, cookingApi)
      .flatMap(maybeUser =>
        ApiRunner.runResponseAsyncSafely(response, cookingApi, maybeUser)
      )
  }

  def deleteSubstitute(
      id: UUID,
      substituteId: UUID
  ): Action[AnyContent] = Action.async { request =>
    val response = cookingApi.ingredients
      .removeSubstitute(id, substituteId)
      .fold(
        error => ErrorMapping.mapCustomErrorsToHttp(error),
        _ => Ok(Json.obj("message" -> "Substitute relationship removed"))
      )
    Requests
      .extractUser(request, cookingApi)
      .flatMap(maybeUser =>
        ApiRunner.runResponseAsyncSafely(response, cookingApi, maybeUser)
      )
  }
}
