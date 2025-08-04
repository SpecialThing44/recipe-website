package http

import api.{Persisting, Querying}
import context.{ApiContext, CookingApi}
import domain.people.users.User
import persistence.users.Users
import io.circe.{Decoder, Encoder}
import io.circe.jawn.decode
import io.circe.syntax.EncoderOps
import play.api.libs.json.{JsValue, Json}
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
    val maybeUser = extractUser(request, cookingApi)
    val entities: ZIO[ApiContext, Throwable, Seq[Entity]] = for {
      entities <- entityApi.list(request.body)
    } yield entities
    val response = entities.fold(
      error => ErrorMapping.mapCustomErrorsToHttp(error),
      result => Ok(s"{ \"Body\": $result }")
    )
    ApiRunner.runResponseSafely[ApiContext](
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
  )(implicit encoder: Encoder[Entity]): Result = {
    val maybeUser = extractUser(request, cookingApi)
    val maybeEntity: ZIO[ApiContext, Throwable, Entity] = for {
      entity <- entityApi.getById(id)
    } yield entity
    val response = maybeEntity.fold(
      error => ErrorMapping.mapCustomErrorsToHttp(error),
      result => Ok(s"{ \"Body\": ${Json.parse(result.asJson.noSpaces)} }")
    )
    ApiRunner.runResponseSafely[ApiContext](
      response,
      cookingApi,
      maybeUser
    )
  }

  def post[Entity: Decoder, EntityInput: Decoder, EntityUpdateInput: Decoder](
      request: Request[JsValue],
      cookingApi: CookingApi,
      entityApi: Persisting[Entity, EntityInput, EntityUpdateInput]
  ): Result = {
    val maybeUser = extractUser(request, cookingApi)
    val createdEntity: ZIO[ApiContext, Throwable, Entity] = for {
      newEntity <- ZIO.fromEither(
        decode[EntityInput](request.body.toString)
      )
      createdEntity <- entityApi.create(newEntity)
    } yield createdEntity
    val response = createdEntity.fold(
      error => ErrorMapping.mapCustomErrorsToHttp(error),
      result => Results.Created(s"{ \"Body\": $result }")
    )
    ApiRunner.runResponseSafely[ApiContext](
      response,
      cookingApi,
      maybeUser
    )
  }

  def put[Entity: Decoder, EntityInput: Decoder, EntityUpdateInput: Decoder](
      id: java.util.UUID,
      request: Request[JsValue],
      cookingApi: CookingApi,
      entityApi: Persisting[Entity, EntityInput, EntityUpdateInput] & Querying[
        Entity
      ]
  )(implicit encoder: Encoder[Entity]): Result = {
    val maybeUser = extractUser(request, cookingApi)
    val maybeUpdatedEntity: ZIO[ApiContext, Throwable, Entity] = for {
      newEntity <- ZIO.fromEither(
        decode[EntityUpdateInput](request.body.toString)
      )
      originalEntity <- entityApi match {
        case userApi: persistence.users.Users =>
          userApi
            .getByIdWithPassword(id)
            .asInstanceOf[ZIO[ApiContext, Throwable, Entity]]
        case _ =>
          entityApi.getById(id)
      }
      updatedEntity <- entityApi.update(newEntity, originalEntity)
    } yield updatedEntity
    val response = maybeUpdatedEntity.fold(
      error => ErrorMapping.mapCustomErrorsToHttp(error),
      result =>
        Results.Created(
          s"{ \"ID\": \"$id\", \"Body\": ${Json.parse(result.asJson.noSpaces)}  }"
        )
    )
    ApiRunner.runResponseSafely[ApiContext](
      response,
      cookingApi,
      maybeUser
    )
  }

  private def extractUser(
      request: Request[Any],
      cookingApi: CookingApi
  ): Option[User] = {
    val authHeader = request.headers.get("Authorization")
    try {
      ApiRunner.runResponse[ApiContext, Throwable, Option[User]](
        cookingApi.users.authenticate(authHeader),
        cookingApi,
        None
      )
    } catch {
      case _: Throwable => None
    }
  }

}
