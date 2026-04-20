package http.tags

import com.google.inject.{Inject, Singleton}
import context.CookingApi
import http.Requests
import play.api.libs.json.*
import play.api.mvc.*

import scala.concurrent.ExecutionContext

@Singleton
class TagsController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi
) extends AbstractController(cc) {
  private implicit val ec: ExecutionContext = cc.executionContext

  def list(): Action[JsValue] = Action.async(parse.json) { request =>
    Requests.list[String](request, cookingApi, cookingApi.tags)
  }
}
