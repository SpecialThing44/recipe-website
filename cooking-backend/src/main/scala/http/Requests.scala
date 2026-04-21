package http

import api.{Listing, Persisting, Querying}
import context.{ApiContext, CookingApi}
import domain.filters.Filters
import domain.users.User
import io.circe.jawn.decode
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Results.Ok
import play.api.mvc.*
import zio.ZIO

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

object Requests {
  def extractUser(
      request: RequestHeader,
      cookingApi: CookingApi
  )(implicit ec: ExecutionContext): Future[Option[User]] = {
    val authHeader = request.headers.get("Authorization")
    ApiRunner
      .runResponseAsync[ApiContext, Throwable, Option[User]](
        cookingApi.users.authenticate(authHeader),
        cookingApi,
        None
      )
      .recover { case _: Throwable => None }
  }

  def list[Entity](
      request: Request[JsValue],
      cookingApi: CookingApi,
      entityApi: Listing[Entity],
      authenticate: Boolean = true
  )(implicit encoder: Encoder[Entity], ec: ExecutionContext): Future[Result] = {
    val maybeUserFuture =
      if (authenticate) extractUser(request, cookingApi)
      else Future.successful(None)
    val response =
      for {
        filters <- ZIO.fromEither(decode[Filters](request.body.toString))
        entities <- entityApi.list(filters)
      } yield Ok(s"{ \"Body\": ${Json.parse(entities.asJson.noSpaces)}}")

    maybeUserFuture.flatMap(maybeUser =>
      ApiRunner.runResponseAsyncSafely[ApiContext](
        response,
        cookingApi,
        maybeUser
      )
    )
  }

  def get[Entity](
      id: UUID,
      request: Request[AnyContent],
      cookingApi: CookingApi,
      entityApi: Querying[Entity],
      authenticate: Boolean = true
  )(implicit encoder: Encoder[Entity], ec: ExecutionContext): Future[Result] = {
    val maybeUserFuture =
      if (authenticate) extractUser(request, cookingApi)
      else Future.successful(None)
    val response = entityApi
      .getById(id)
      .map(entity => Ok(s"{ \"Body\": ${Json.parse(entity.asJson.noSpaces)} }"))

    maybeUserFuture.flatMap(maybeUser =>
      ApiRunner.runResponseAsyncSafely[ApiContext](
        response,
        cookingApi,
        maybeUser
      )
    )
  }

  def post[Entity: Decoder, EntityInput: Decoder, EntityUpdateInput: Decoder](
      request: Request[JsValue],
      cookingApi: CookingApi,
      entityApi: Persisting[Entity, EntityInput, EntityUpdateInput]
  )(implicit encoder: Encoder[Entity], ec: ExecutionContext): Future[Result] = {
    val response =
      for {
        newEntity <- ZIO.fromEither(
          decode[EntityInput](request.body.toString)
        )
        createdEntity <- entityApi.create(newEntity)
      } yield Results.Created(
        s"{ \"Body\": ${Json.parse(createdEntity.asJson.noSpaces)} }"
      )

    extractUser(request, cookingApi).flatMap(maybeUser =>
      ApiRunner.runResponseAsyncSafely[ApiContext](
        response,
        cookingApi,
        maybeUser
      )
    )
  }

  def put[Entity: Decoder, EntityInput: Decoder, EntityUpdateInput: Decoder](
      id: java.util.UUID,
      request: Request[JsValue],
      cookingApi: CookingApi,
      entityApi: Persisting[Entity, EntityInput, EntityUpdateInput] & Querying[
        Entity
      ]
  )(implicit encoder: Encoder[Entity], ec: ExecutionContext): Future[Result] = {
    val response =
      for {
        newEntity <- ZIO.fromEither(
          decode[EntityUpdateInput](request.body.toString)
        )
        originalEntity <- entityApi.getById(id)
        updatedEntity <- entityApi.update(newEntity, originalEntity)
      } yield Results.Created(
        s"{ \"ID\": \"$id\", \"Body\": ${Json.parse(updatedEntity.asJson.noSpaces)}  }"
      )

    extractUser(request, cookingApi).flatMap(maybeUser =>
      ApiRunner.runResponseAsyncSafely[ApiContext](
        response,
        cookingApi,
        maybeUser
      )
    )
  }

  def delete[Entity](
      id: java.util.UUID,
      request: Request[AnyContent],
      cookingApi: CookingApi,
      entityApi: Persisting[Entity, ?, ?] & Querying[Entity]
  )(implicit encoder: Encoder[Entity], ec: ExecutionContext): Future[Result] = {
    val response = entityApi.delete(id).map { deletedEntity =>
      Results.Ok(
        s"{ \"ID\": \"$id\", \"Body\": ${Json.parse(deletedEntity.asJson.noSpaces)}  }"
      )
    }

    extractUser(request, cookingApi).flatMap(maybeUser =>
      ApiRunner.runResponseAsyncSafely[ApiContext](
        response,
        cookingApi,
        maybeUser
      )
    )
  }

}
