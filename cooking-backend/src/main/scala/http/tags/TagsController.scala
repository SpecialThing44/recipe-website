package http.tags

import com.google.inject.{Inject, Singleton}
import context.CookingApi
import http.Requests
import play.api.libs.json.*
import play.api.mvc.*

@Singleton
class TagsController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi
) extends AbstractController(cc) {
  def list(): Action[JsValue] = Action(parse.json) { request =>
    Requests.list[String](request, cookingApi, cookingApi.tags)
  }
}
