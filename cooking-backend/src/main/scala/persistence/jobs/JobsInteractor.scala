package persistence.jobs

import com.google.inject.Inject
import persistence.neo4j.Database
import zio.{Task, ZIO}

import java.util.UUID

case class JobStatus(
    jobId: String,
    status: String,
    processedEvents: Int,
    statsJson: Option[String],
    error: Option[String],
    createdAt: String,
    startedAt: Option[String],
    finishedAt: Option[String]
)

class JobsInteractor @Inject() (
    database: Database
) {
  def runManagedJob(jobId: String, jobName: String)(
      runWork: Task[(Int, String)]
  ): Task[Unit] = {
    val run =
      for {
        lockAcquired <- tryAcquireLock(jobName, jobId)
        _ <-
          if (!lockAcquired)
            markJobFailed(
              jobName,
              jobId,
              "Another processor is already running"
            )
          else
            for {
              _ <- markJobRunning(jobName, jobId)
              result <- runWork
              (processedEvents, statsJson) = result
              _ <- markJobDone(
                jobName,
                jobId,
                processedEvents,
                statsJson
              )
            } yield ()
      } yield ()

    run.catchAll(error =>
      markJobFailed(jobName, jobId, error.getMessage).orDie *> ZIO.fail(error)
    ).ensuring(releaseLock(jobName, jobId).ignore)
  }

  def createQueuedJob(
      jobId: String,
      jobName: String,
      requestedBy: UUID
  ): Task[Unit] =
    database.writeTransaction(
      s"""
         |CREATE (j:IngredientWeightJob {
         |  jobId: $$jobId,
         |  jobType: $$jobName,
         |  status: 'queued',
         |  requestedBy: $$requestedBy,
         |  createdAt: datetime(),
         |  createdOn: datetime()
         |})
         |RETURN j.jobId AS jobId
         |""".stripMargin,
      Map(
        "jobId" -> jobId,
        "jobName" -> jobName,
        "requestedBy" -> requestedBy.toString
      ),
      (_: org.neo4j.driver.Result) => ()
    )

  def tryAcquireLock(jobName: String, jobId: String): Task[Boolean] =
    database.writeTransaction(
      s"""
         |MERGE (l:AsyncJobLock {name: $$jobName})
         |WITH l
         |WHERE coalesce(l.locked, false) = false
         |SET l.locked = true,
         |    l.holderJobId = $$jobId,
         |    l.lockedOn = datetime()
         |RETURN l.name AS lockName
         |""".stripMargin,
      Map(
        "jobName" -> jobName,
        "jobId" -> jobId
      ),
      (result: org.neo4j.driver.Result) => result.hasNext
    )

  def releaseLock(jobName: String, jobId: String): Task[Unit] =
    database.writeTransaction(
      s"""
         |MATCH (l:AsyncJobLock {name: $$jobName})
         |WHERE l.holderJobId = $$jobId
         |SET l.locked = false,
         |    l.holderJobId = NULL,
         |    l.lockedOn = NULL
         |RETURN l.name AS lockName
         |""".stripMargin,
      Map(
        "jobName" -> jobName,
        "jobId" -> jobId
      ),
      (_: org.neo4j.driver.Result) => ()
    )

  def markJobRunning(jobName: String, jobId: String): Task[Unit] =
    database.writeTransaction(
      s"""
         |MATCH (j:IngredientWeightJob {jobId: $$jobId})
         |WHERE j.jobType = $$jobName
         |SET j.status = 'running',
         |    j.startedAt = datetime(),
         |    j.startedOn = datetime()
         |RETURN j.jobId AS jobId
         |""".stripMargin,
      Map(
        "jobId" -> jobId,
        "jobName" -> jobName
      ),
      (_: org.neo4j.driver.Result) => ()
    )

  def markJobDone(
      jobName: String,
      jobId: String,
      processedEvents: Int,
      statsJson: String
  ): Task[Unit] =
    database.writeTransaction(
      s"""
         |MATCH (j:IngredientWeightJob {jobId: $$jobId})
         |WHERE j.jobType = $$jobName
         |SET j.status = 'done',
         |    j.processedEvents = $$processedEvents,
         |    j.statsJson = $$statsJson,
         |    j.finishedAt = datetime(),
         |    j.finishedOn = datetime()
         |RETURN j.jobId AS jobId
         |""".stripMargin,
      Map(
        "jobName" -> jobName,
        "jobId" -> jobId,
        "processedEvents" -> Int.box(processedEvents),
        "statsJson" -> statsJson
      ),
      (_: org.neo4j.driver.Result) => ()
    )

  def markJobFailed(
      jobName: String,
      jobId: String,
      error: String
  ): Task[Unit] = {
    val sanitized = Option(error).getOrElse("unknown")
    database.writeTransaction(
      s"""
         |MATCH (j:IngredientWeightJob {jobId: $$jobId})
         |WHERE j.jobType = $$jobName
         |SET j.status = 'failed',
         |    j.error = $$error,
         |    j.finishedAt = datetime(),
         |    j.finishedOn = datetime()
         |RETURN j.jobId AS jobId
         |""".stripMargin,
      Map(
        "jobName" -> jobName,
        "jobId" -> jobId,
        "error" -> sanitized
      ),
      (_: org.neo4j.driver.Result) => ()
    )
  }
}
