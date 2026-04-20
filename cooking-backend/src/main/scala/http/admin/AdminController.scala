package http.admin

import api.users.AuthenticationInteractor
import com.google.inject.{Inject, Singleton}
import context.CookingApi
import http.Requests.extractUser
import persistence.ingredients.weights.IngredientWeightAsyncService
import play.api.libs.json.{JsValue, Json, Reads}
import play.api.mvc.*

@Singleton
class AdminController @Inject() (
    cc: ControllerComponents,
    cookingApi: CookingApi,
    ingredientWeightAsyncService: IngredientWeightAsyncService
) extends AbstractController(cc) {

  private case class IngredientWeightSettingsInput(meanRawPenaltyFactor: Double)
  private implicit val ingredientWeightSettingsReads
      : Reads[IngredientWeightSettingsInput] =
    Json.reads[IngredientWeightSettingsInput]

  def processIngredientWeightEvents(): Action[AnyContent] = Action { request =>
    val maybeUser = extractUser(request, cookingApi)
    maybeUser match {
      case Some(user) =>
        domain.types.ZIORuntime.unsafeRun(
          AuthenticationInteractor.ensureIsAdmin(user).either
        ) match {
          case Right(_) =>
            val jobId = domain.types.ZIORuntime.unsafeRun(
              ingredientWeightAsyncService.triggerProcessPendingEvents(user.id)
            )
            Accepted(
              Json.obj(
                "status" -> "queued",
                "jobId" -> jobId
              )
            )
          case Left(_) =>
            Forbidden(Json.obj("error" -> "Admin privileges required"))
        }
      case None =>
        Unauthorized(Json.obj("error" -> "Invalid or missing token"))
    }
  }

  def rebuildIngredientWeights(): Action[AnyContent] = Action { request =>
    val maybeUser = extractUser(request, cookingApi)
    maybeUser match {
      case Some(user) =>
        domain.types.ZIORuntime.unsafeRun(
          AuthenticationInteractor.ensureIsAdmin(user).either
        ) match {
          case Right(_) =>
            val jobId = domain.types.ZIORuntime.unsafeRun(
              ingredientWeightAsyncService.triggerRebuildAllIngredients(user.id)
            )
            Accepted(
              Json.obj(
                "status" -> "queued",
                "jobId" -> jobId
              )
            )
          case Left(_) =>
            Forbidden(Json.obj("error" -> "Admin privileges required"))
        }
      case None =>
        Unauthorized(Json.obj("error" -> "Invalid or missing token"))
    }
  }

  def ingredientWeightJobStatus(jobId: String): Action[AnyContent] = Action {
    request =>
      val maybeUser = extractUser(request, cookingApi)
      maybeUser match {
        case Some(user) =>
          domain.types.ZIORuntime.unsafeRun(
            AuthenticationInteractor.ensureIsAdmin(user).either
          ) match {
            case Right(_) =>
              val jobStatus = domain.types.ZIORuntime.unsafeRun(
                ingredientWeightAsyncService.getJobStatus(jobId)
              )
              jobStatus match {
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
            case Left(_) =>
              Forbidden(Json.obj("error" -> "Admin privileges required"))
          }
        case None =>
          Unauthorized(Json.obj("error" -> "Invalid or missing token"))
      }
  }

  def activeIngredientWeightJobIds(): Action[AnyContent] = Action { request =>
    val maybeUser = extractUser(request, cookingApi)
    maybeUser match {
      case Some(user) =>
        domain.types.ZIORuntime.unsafeRun(
          AuthenticationInteractor.ensureIsAdmin(user).either
        ) match {
          case Right(_) =>
            val jobIds = domain.types.ZIORuntime.unsafeRun(
              ingredientWeightAsyncService.getActiveJobIds()
            )
            Ok(Json.obj("jobIds" -> jobIds))
          case Left(_) =>
            Forbidden(Json.obj("error" -> "Admin privileges required"))
        }
      case None =>
        Unauthorized(Json.obj("error" -> "Invalid or missing token"))
    }
  }

  def getIngredientWeightSettings(): Action[AnyContent] = Action { request =>
    val maybeUser = extractUser(request, cookingApi)
    maybeUser match {
      case Some(user) =>
        domain.types.ZIORuntime.unsafeRun(
          AuthenticationInteractor.ensureIsAdmin(user).either
        ) match {
          case Right(_) =>
            val meanRawPenaltyFactor =
              domain.types.ZIORuntime.unsafeRun(
                ingredientWeightAsyncService.getMeanRawPenaltyFactor()
              )
            Ok(
              Json.obj(
                "meanRawPenaltyFactor" -> meanRawPenaltyFactor
              )
            )
          case Left(_) =>
            Forbidden(Json.obj("error" -> "Admin privileges required"))
        }
      case None =>
        Unauthorized(Json.obj("error" -> "Invalid or missing token"))
    }
  }

  def updateIngredientWeightSettings(): Action[JsValue] = Action(parse.json) {
    request =>
      val maybeUser = extractUser(request, cookingApi)
      maybeUser match {
        case Some(user) =>
          domain.types.ZIORuntime.unsafeRun(
            AuthenticationInteractor.ensureIsAdmin(user).either
          ) match {
            case Right(_) =>
              request.body
                .validate[IngredientWeightSettingsInput]
                .fold(
                  errors =>
                    BadRequest(
                      Json.obj(
                        "error" -> "Invalid request body",
                        "details" -> errors.toString
                      )
                    ),
                  payload => {
                    try {
                      val meanRawPenaltyFactor =
                        domain.types.ZIORuntime.unsafeRun(
                          ingredientWeightAsyncService.setMeanRawPenaltyFactor(
                            payload.meanRawPenaltyFactor
                          )
                        )
                      Ok(
                        Json.obj(
                          "meanRawPenaltyFactor" -> meanRawPenaltyFactor
                        )
                      )
                    } catch {
                      case e: IllegalArgumentException =>
                        BadRequest(Json.obj("error" -> e.getMessage))
                    }
                  }
                )
            case Left(_) =>
              Forbidden(Json.obj("error" -> "Admin privileges required"))
          }
        case None =>
          Unauthorized(Json.obj("error" -> "Invalid or missing token"))
      }
  }
}
