package http.admin

import api.users.AuthenticationInteractor
import com.google.inject.{Inject, Singleton}
import context.CookingApi
import domain.users.User
import http.Requests.extractUser
import http.{ApiRunner, Requests}
import persistence.ingredients.weights.IngredientWeightJobInteractor
import play.api.libs.json.Json
import play.api.mvc.*
import zio.ZIO

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AdminController @Inject() (
                                  cc: ControllerComponents,
                                  cookingApi: CookingApi,
                                  ingredientWeightJobInteractor: IngredientWeightJobInteractor
) extends AbstractController(cc) {
  private implicit val ec: ExecutionContext = cc.executionContext

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
        ingredientWeightJobInteractor
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
      ingredientWeightJobInteractor
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
        ingredientWeightJobInteractor.getJobStatus(jobId).map {
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
        ingredientWeightJobInteractor
          .getActiveJobIds()
          .map(jobIds => Ok(Json.obj("jobIds" -> jobIds)))
      }
  }
}
