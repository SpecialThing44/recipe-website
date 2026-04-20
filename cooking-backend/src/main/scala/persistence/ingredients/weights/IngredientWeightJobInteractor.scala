package persistence.ingredients.weights

import com.google.inject.{Inject, Singleton}
import domain.types.ZIORuntime
import persistence.jobs.{JobStatus, JobsInteractor}
import persistence.neo4j.Database
import play.api.Configuration
import zio.Task

import java.util.UUID
import scala.jdk.CollectionConverters.*

@Singleton
class IngredientWeightJobInteractor @Inject()(
    database: Database,
    config: Configuration,
  jobsInteractor: JobsInteractor,
  processPendingEventsJob: ProcessPendingEventsJob,
  rebuildAllIngredientsJob: RebuildAllIngredientsJob
) {

  private val processPendingEventsJobName = "process_pending_events"
  private val rebuildAllIngredientsJobName = "rebuild_all"

  private val defaultMeanRawWeightFactor = config.getOptional[Double]("ingredientWeights.meanRawPenaltyFactor").getOrElse(3.0)

  if (defaultMeanRawWeightFactor < 0.0) {
    throw new IllegalArgumentException(
      "ingredientWeights.meanRawPenaltyFactor must be greater than or equal to 0"
    )
  }

  def triggerProcessPendingEvents(requestedBy: UUID): Task[String] = {
    val jobId = UUID.randomUUID().toString
    val createJob =
      jobsInteractor.createQueuedJob(jobId, processPendingEventsJobName, requestedBy)

    createJob.map { _ =>
      val _ = ZIORuntime.unsafeFork(
        jobsInteractor
          .runManagedJob(jobId, processPendingEventsJobName) {
            processPendingEventsJob.run(defaultMeanRawWeightFactor)
          }
          .orDie
      )
      jobId
    }
  }

  def triggerRebuildAllIngredients(requestedBy: UUID): Task[String] = {
    val jobId = UUID.randomUUID().toString
    val createJob =
      jobsInteractor.createQueuedJob(jobId, rebuildAllIngredientsJobName, requestedBy)

    createJob.map { _ =>
      val _ = ZIORuntime.unsafeFork(
        jobsInteractor
          .runManagedJob(jobId, rebuildAllIngredientsJobName) {
            rebuildAllIngredientsJob.run(defaultMeanRawWeightFactor)
          }
          .orDie
      )
      jobId
    }
  }

  def getJobStatus(jobId: String): Task[Option[JobStatus]] =
    database.readTransaction(
      s"""
         |MATCH (j:IngredientWeightJob {jobId: $$jobId})
         |RETURN j.jobId AS jobId,
         |       j.status AS status,
         |       coalesce(j.processedEvents, 0) AS processedEvents,
         |       j.statsJson AS statsJson,
         |       j.error AS error,
         |       toString(coalesce(j.createdAt, j.createdOn)) AS createdAt,
         |       CASE WHEN coalesce(j.startedAt, j.startedOn) IS NULL THEN NULL ELSE toString(coalesce(j.startedAt, j.startedOn)) END AS startedAt,
         |       CASE WHEN coalesce(j.finishedAt, j.finishedOn) IS NULL THEN NULL ELSE toString(coalesce(j.finishedAt, j.finishedOn)) END AS finishedAt
         |""".stripMargin,
      Map("jobId" -> jobId),
      (result: org.neo4j.driver.Result) =>
        if (!result.hasNext) None
        else {
          val record = result.next()
          Some(
            JobStatus(
              jobId = record.get("jobId").asString(),
              status = record.get("status").asString(),
              processedEvents = record.get("processedEvents").asInt(),
              statsJson =
                if (record.get("statsJson").isNull) None
                else Some(record.get("statsJson").asString()),
              error =
                if (record.get("error").isNull) None
                else Some(record.get("error").asString()),
              createdAt = record.get("createdAt").asString(),
              startedAt =
                if (record.get("startedAt").isNull) None
                else Some(record.get("startedAt").asString()),
              finishedAt =
                if (record.get("finishedAt").isNull) None
                else Some(record.get("finishedAt").asString())
            )
          )
        }
    )

  def getActiveJobIds(): Task[Seq[String]] =
    database.readTransaction(
      """
        |MATCH (j:IngredientWeightJob)
        |WHERE j.status IN ['queued', 'running']
        |RETURN j.jobId AS jobId
        |ORDER BY coalesce(j.createdAt, j.createdOn) DESC
        |""".stripMargin,
      (result: org.neo4j.driver.Result) =>
        result.asScala.map(record => record.get("jobId").asString()).toSeq
    )
}
