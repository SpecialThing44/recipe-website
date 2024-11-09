package http

import api.{Persisting, Querying}
import context.{ApiContext, CookingApi}
import http.authentication.UserAuthentication
import io.circe.Decoder
import io.circe.jawn.decode
import play.api.libs.json.JsValue
import play.api.mvc.Results.Ok
import play.api.mvc.{AnyContent, Request, Result, Results}
import zio.ZIO

import java.util.UUID

object Requests {
  def list[Entity](
      request: Request[JsValue],
      cookingApi: CookingApi,
      entityApi: Querying[Entity]
  ): Result = {
    val maybeUser = UserAuthentication.getMaybeUser(request, cookingApi)
    val entities: ZIO[ApiContext, Throwable, Seq[Entity]] = for {
      entities <- entityApi.list(request.body)
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

  def get[Entity](
      id: UUID,
      request: Request[AnyContent],
      cookingApi: CookingApi,
      entityApi: Querying[Entity]
  ): Result = {
    val maybeUser = UserAuthentication.getMaybeUser(request, cookingApi)
    val maybeEntity: ZIO[ApiContext, Throwable, Entity] = for {
      entity <- entityApi.getById(id)
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
    val createdEntity: ZIO[ApiContext, Throwable, Entity] = for {
      newEntity <- ZIO.fromEither(
        decode[Entity](request.body.toString)
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
      entityApi: Persisting[Entity] & Querying[Entity]
  ): Result = {
    val maybeUser = UserAuthentication.getMaybeUser(request, cookingApi)
    val maybeUpdatedEntity: ZIO[ApiContext, Throwable, Entity] = for {
      newEntity <- ZIO.fromEither(decode[Entity](request.body.toString))
      originalEntity <- entityApi.getById(id)
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
