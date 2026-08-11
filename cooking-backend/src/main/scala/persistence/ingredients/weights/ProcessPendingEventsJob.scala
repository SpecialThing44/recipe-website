package persistence.ingredients.weights

import com.google.inject.{Inject, Singleton}
import domain.logging.Logging
import persistence.neo4j.Database
import play.api.Configuration
import zio.{Task, ZIO}

import scala.jdk.CollectionConverters.*

@Singleton
class ProcessPendingEventsJob @Inject() (
    database: Database,
    config: Configuration,
    rebuildAllIngredientsJob: RebuildAllIngredientsJob,
    ingredientWeightEventInteractor: IngredientWeightEventInteractor
) extends Logging {

  private case class PendingEvent(
      eventId: String,
      attempts: Int
  )

  private val batchSize =
    config.getOptional[Int]("ingredientWeights.batchSize").getOrElse(100)
  private val maxAttempts =
    config.getOptional[Int]("ingredientWeights.maxAttempts").getOrElse(5)
  private val baseBackoffSeconds =
    config.getOptional[Int]("ingredientWeights.backoffSeconds").getOrElse(15)

  def run(meanRawPenaltyFactor: Double): Task[(Int, String)] =
    for {
      processed <- loopWithFactor(0, meanRawPenaltyFactor)
    } yield (processed, s"{\"processedEvents\":$processed}")

  private def loopWithFactor(
      processed: Int,
      meanRawPenaltyFactor: Double
  ): Task[Int] =
    for {
      pending <- claimPendingEvents(batchSize)
      batchCount <-
        if (pending.isEmpty) ZIO.succeed(0)
        else
          rebuildAllIngredientsJob
            .run(meanRawPenaltyFactor)
            .flatMap(_ => markBatchDone(pending).as(pending.size))
            .catchAll(error => {
              logger.error(
                s"Failed processing ingredient weight batch (${pending.size} events): ${error.getMessage}"
              )
              markBatchRetryOrFailed(pending, error.getMessage).as(0)
            })
      totalProcessed = processed + batchCount
      finalProcessed <-
        if (pending.isEmpty) ZIO.succeed(totalProcessed)
        else loopWithFactor(totalProcessed, meanRawPenaltyFactor)
    } yield finalProcessed

  private def claimPendingEvents(limit: Int): Task[Seq[PendingEvent]] =
    database.writeTransaction(
      s"""
         |MATCH (e:IngredientWeightEvent)
         |WHERE (
         |  e.status = 'pending' OR
         |  (e.status = 'retry' AND coalesce(e.nextEligibleAt, e.nextRetryOn) IS NOT NULL AND coalesce(e.nextEligibleAt, e.nextRetryOn) <= datetime())
         |)
         |  AND coalesce(e.attempts, 0) < $$maxAttempts
         |WITH e
         |ORDER BY coalesce(e.createdAt, e.createdOn) ASC
         |LIMIT $$limit
         |SET e.status = 'processing',
         |    e.startedAt = datetime(),
         |    e.startedOn = datetime(),
         |    e.attempts = coalesce(e.attempts, 0) + 1
         |RETURN e.eventId AS eventId, e.attempts AS attempts
         |""".stripMargin,
      Map(
        "maxAttempts" -> Int.box(maxAttempts),
        "limit" -> Int.box(limit)
      ),
      (result: org.neo4j.driver.Result) =>
        result.asScala
          .map(record => {
            val eventId = record.get("eventId").asString()
            val attempts = record.get("attempts").asInt()
            PendingEvent(eventId, attempts)
          })
          .toSeq
    )

  private def markBatchDone(events: Seq[PendingEvent]): Task[Unit] =
    zio.ZIO
      .foreach(events)(event =>
        ingredientWeightEventInteractor.markEventDone(event.eventId)
      )
      .unit

  private def markBatchRetryOrFailed(
      events: Seq[PendingEvent],
      error: String
  ): Task[Unit] =
    zio.ZIO
      .foreach(events) { event =>
        ingredientWeightEventInteractor.markEventRetryOrFailed(
          event.eventId,
          event.attempts,
          maxAttempts,
          baseBackoffSeconds,
          error
        )
      }
      .unit

}
