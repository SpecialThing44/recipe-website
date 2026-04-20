package http.admin

import api.users.AuthenticationInteractor
import com.google.inject.{Inject, Singleton}
import context.CookingApi
import domain.users.User
import http.Requests.extractUser
import http.{ApiRunner, Requests}
import persistence.ingredients.weights.IngredientWeightAsyncService
import play.api.libs.json.{JsValue, Json, Reads}
import play.api.mvc.*
import zio.ZIO

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AdminController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi,
    ingredientWeightAsyncService: IngredientWeightAsyncService
) extends AbstractController(cc) {
  private implicit val ec: ExecutionContext = cc.executionContext

  private case class IngredientWeightSettingsInput(meanRawPenaltyFactor: Double)
  private implicit val ingredientWeightSettingsReads
      : Reads[IngredientWeightSettingsInput] =
    Json.reads[IngredientWeightSettingsInput]

  private def withAdminUser(request: RequestHeader)(
      onAuthorized: User => ZIO[context.ApiContext, Throwable, Result]
  ): Future[Result] = {
    extractUser(request, cookingApi).flatMap {
      case Some(user) =>
        val response = AuthenticationInteractor
          .ensureIsAdmin(user)
          .either
          .flatMap {
            case Right(_) => onAuthorized(user)
            case Left(_) =>
              ZIO.succeed(
                Forbidden(Json.obj("error" -> "Admin privileges required"))
              )
          }

        ApiRunner.runResponseAsyncSafely(response, cookingApi, Some(user))
      case None =>
        Future.successful(
          Unauthorized(Json.obj("error" -> "Invalid or missing token"))
        )
    }
  }

  def processIngredientWeightEvents(): Action[AnyContent] = Action.async {
    request =>
      withAdminUser(request) { user =>
        ingredientWeightAsyncService
          .triggerProcessPendingEvents(user.id)
          .map(jobId =>
            Accepted(
              Json.obj(
                "status" -> "queued",
                "jobId" -> jobId
              )
            )
          )
      }
  }

  def rebuildIngredientWeights(): Action[AnyContent] = Action.async { request =>
    withAdminUser(request) { user =>
      ingredientWeightAsyncService
        .triggerRebuildAllIngredients(user.id)
        .map(jobId =>
          Accepted(
            Json.obj(
              "status" -> "queued",
              "jobId" -> jobId
            )
          )
        )
    }
  }

  def ingredientWeightJobStatus(jobId: String): Action[AnyContent] =
    Action.async { request =>
      withAdminUser(request) { _ =>
        ingredientWeightAsyncService.getJobStatus(jobId).map {
          case Some(job) =>
            Ok(
              Json.obj(
                "jobId" -> job.jobId,
                "status" -> job.status,
                "processedEvents" -> job.processedEvents,
                "statsJson" -> job.statsJson,
                "error" -> job.error,
                "createdAt" -> job.createdAt,
                "startedAt" -> job.startedAt,
                "finishedAt" -> job.finishedAt
              )
            )
          case None =>
            NotFound(Json.obj("error" -> s"Job not found: $jobId"))
        }
      }
    }

  def activeIngredientWeightJobIds(): Action[AnyContent] = Action.async {
    request =>
      withAdminUser(request) { _ =>
        ingredientWeightAsyncService
          .getActiveJobIds()
          .map(jobIds => Ok(Json.obj("jobIds" -> jobIds)))
      }
  }

  def getIngredientWeightSettings(): Action[AnyContent] = Action.async {
    request =>
      withAdminUser(request) { _ =>
        ingredientWeightAsyncService.getMeanRawPenaltyFactor().map {
          meanRawPenaltyFactor =>
            Ok(
              Json.obj(
                "meanRawPenaltyFactor" -> meanRawPenaltyFactor
              )
            )
        }
      }
  }

  def updateIngredientWeightSettings(): Action[JsValue] =
    Action.async(parse.json) { request =>
      request.body
        .validate[IngredientWeightSettingsInput]
        .fold(
          errors =>
            Future.successful(
              BadRequest(
                Json.obj(
                  "error" -> "Invalid request body",
                  "details" -> errors.toString
                )
              )
            ),
          payload => {
            withAdminUser(request) { _ =>
              ingredientWeightAsyncService
                .setMeanRawPenaltyFactor(payload.meanRawPenaltyFactor)
                .map(meanRawPenaltyFactor =>
                  Ok(
                    Json.obj(
                      "meanRawPenaltyFactor" -> meanRawPenaltyFactor
                    )
                  )
                )
                .catchSome { case e: IllegalArgumentException =>
                  ZIO.succeed(BadRequest(Json.obj("error" -> e.getMessage)))
                }
            }
          }
        )
    }
}
