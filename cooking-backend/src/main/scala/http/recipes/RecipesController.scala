package http.recipes
import com.google.inject.{Inject, Singleton}
import context.CookingApi
import domain.food.recipes.Recipe
import http.authentication.UserAuthentication
import http.{ApiRunner, ErrorMapping}
import io.circe.parser.decode
import play.api.libs.json._
import play.api.mvc._
import zio._

@Singleton
class RecipesController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi
) extends AbstractController(cc) {

  def get(): Action[JsValue] = Action(parse.json) { request =>
    val maybeUser = UserAuthentication.getMaybeUser(request, cookingApi)
    val jsonBody: JsValue = request.body
    val recipes = for {
      recipes <- cookingApi.recipes.list(jsonBody)
    } yield recipes
    val response = recipes.fold(
      error => ErrorMapping.mapCustomErrorsToHttp(error),
      result => Ok(s"Body: $result")
    )
    ApiRunner.runResponse(response, cookingApi, maybeUser)

  }

  def post(): Action[JsValue] = Action(parse.json) { request =>
    val maybeUser = UserAuthentication.getMaybeUser(request, cookingApi)
    val jsonBody: JsValue = request.body
    val createdRecipe = for {
      newRecipe <- ZIO.fromEither(decode[Recipe](jsonBody.toString))
      createdRecipe <- cookingApi.recipes.create(newRecipe)
    } yield createdRecipe
    val response = createdRecipe.fold(
      error => ErrorMapping.mapCustomErrorsToHttp(error),
      result => Created(s"Body: $result")
    )
    ApiRunner.runResponse(response, cookingApi, maybeUser)
  }

  def get(id: java.util.UUID): Action[JsValue] = Action(parse.json) { request =>
    val maybeUser = UserAuthentication.getMaybeUser(request, cookingApi)
    val maybeRecipe = for {
      recipeFromId <- cookingApi.recipes.get(id)
    } yield recipeFromId
    val response = maybeRecipe.fold(
      error => ErrorMapping.mapCustomErrorsToHttp(error),
      result => Ok(s"ID: $id, Body: $result")
    )
    ApiRunner.runResponse(response, cookingApi, maybeUser)

  }

  def put(id: java.util.UUID): Action[JsValue] = Action(parse.json) { request =>
    val maybeUser = UserAuthentication.getMaybeUser(request, cookingApi)
    val jsonBody: JsValue = request.body
    val maybeUpdatedRecipe = for {
      newRecipe <- ZIO.fromEither(decode[Recipe](jsonBody.toString))
      originalRecipe <- cookingApi.recipes.get(id)
      updatedRecipe <- cookingApi.recipes.update(originalRecipe, newRecipe)
    } yield updatedRecipe
    val response = maybeUpdatedRecipe.fold(
      error => ErrorMapping.mapCustomErrorsToHttp(error),
      result => Created(s"ID: $id, Body: $result")
    )
    ApiRunner.runResponse(response, cookingApi, maybeUser)

  }
}
