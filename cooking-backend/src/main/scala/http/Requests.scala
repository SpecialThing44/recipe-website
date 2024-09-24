package http

import api.Querying
import context.{ApiContext, CookingApi}
import http.authentication.UserAuthentication
import io.circe.Decoder
import io.circe.jawn.decode
import persistence.Persisting
import play.api.libs.json.JsValue
import play.api.mvc.Results.Ok
import play.api.mvc.{Request, Result, Results}
import zio.ZIO

import java.util.UUID

object Requests {
  def get[Entity](
      request: Request[JsValue],
      cookingApi: CookingApi,
      entityApi: Querying[Entity]
  ): Result = {
    val maybeUser = UserAuthentication.getMaybeUser(request, cookingApi)
    val jsonBody: JsValue = request.body
    val entities = for {
      entities <- entityApi.list(jsonBody)
    } yield entities
    val response = entities.fold(
      error => ErrorMapping.mapCustomErrorsToHttp(error),
      result => Ok(s"Body: $result")
    )
    ApiRunner.runResponse[ApiContext, Throwable, Result](
      response,
      cookingApi,
      maybeUser
    )
  }

  def getById[Entity](
      id: UUID,
      request: Request[JsValue],
      cookingApi: CookingApi,
      entityApi: Querying[Entity]
  ): Result = {
    val maybeUser = UserAuthentication.getMaybeUser(request, cookingApi)
    val maybeEntity = for {
      entity <- entityApi.get(id)
    } yield entity
    val response = maybeEntity.fold(
      error => ErrorMapping.mapCustomErrorsToHttp(error),
      result => Ok(s"ID: $id, Body: $result")
    )
    ApiRunner.runResponse[ApiContext, Throwable, Result](
      response,
      cookingApi,
      maybeUser
    )
  }

  def post[Entity: Decoder](
      request: Request[JsValue],
      cookingApi: CookingApi,
      entityApi: Persisting[Entity]
  ): Result = {
    val maybeUser = UserAuthentication.getMaybeUser(request, cookingApi)
    val jsonBody: JsValue = request.body
    val createdEntity = for {
      newEntity <- ZIO.fromEither(
        decode[Entity](jsonBody.toString)
      )
      createdEntity <- entityApi.create(newEntity)
    } yield createdEntity
    val response = createdEntity.fold(
      error => ErrorMapping.mapCustomErrorsToHttp(error),
      result => Results.Created(s"Body: $result")
    )
    ApiRunner.runResponse[ApiContext, Throwable, Result](
      response,
      cookingApi,
      maybeUser
    )
  }

  def put[Entity: Decoder](
      id: java.util.UUID,
      request: Request[JsValue],
      cookingApi: CookingApi,
      entityApi: Persisting[Entity] with Querying[Entity]
  ): Result = {
    val maybeUser = UserAuthentication.getMaybeUser(request, cookingApi)
    val jsonBody: JsValue = request.body
    val maybeUpdatedEntity = for {
      newEntity <- ZIO.fromEither(decode[Entity](jsonBody.toString))
      originalEntity <- entityApi.get(id)
      updatedEntity <- entityApi.update(originalEntity, newEntity)
    } yield updatedEntity
    val response = maybeUpdatedEntity.fold(
      error => ErrorMapping.mapCustomErrorsToHttp(error),
      result => Results.Created(s"ID: $id, Body: $result")
    )
    ApiRunner.runResponse[ApiContext, Throwable, Result](
      response,
      cookingApi,
      maybeUser
    )
  }

}
