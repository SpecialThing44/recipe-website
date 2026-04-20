package persistence.ingredients.weights

import com.google.inject.{Inject, Singleton}
import domain.logging.Logging
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser.decode
import io.circe.Decoder
import persistence.neo4j.Database
import persistence.recipes.RecipePersistence
import play.api.Configuration
import zio.{Task, ZIO}

import scala.jdk.CollectionConverters.*

@Singleton
class ProcessPendingEventsJob @Inject() (
    database: Database,
    config: Configuration,
    recipePersistence: RecipePersistence,
    ingredientWeightEventInteractor: IngredientWeightEventInteractor
) extends Logging {

  private case class IngredientVectorItem(
      ingredientId: String,
      rawNormalizedWeight: Double
  )

  private case class IngredientDelta(
      ingredientId: String,
      recipeCountDelta: Int,
      rawDelta: Double
  )

  private case class PendingEvent(
      eventId: String,
      beforeVector: Seq[IngredientVectorItem],
      afterVector: Seq[IngredientVectorItem],
      attempts: Int
  )

  private given Decoder[IngredientVectorItem] = deriveDecoder[IngredientVectorItem]

  private val batchSize =
    config.getOptional[Int]("ingredientWeights.batchSize").getOrElse(100)
  private val maxAttempts =
    config.getOptional[Int]("ingredientWeights.maxAttempts").getOrElse(5)
  private val baseBackoffSeconds =
    config.getOptional[Int]("ingredientWeights.backoffSeconds").getOrElse(15)

  def run(meanRawPenaltyFactor: Double): Task[(Int, String)] =
    for {
      totalRecipes <- recipePersistence.getTotalRecipeCount()
      processed <- loopWithFactor(0, meanRawPenaltyFactor, totalRecipes)
    } yield (processed, s"{\"processedEvents\":$processed}")

  private def loopWithFactor(
      processed: Int,
      meanRawPenaltyFactor: Double,
      totalRecipes: Int
  ): Task[Int] =
    for {
      pending <- claimPendingEvents(batchSize)
      batchCount <-
        if (pending.isEmpty) ZIO.succeed(0)
        else
          applyIngredientDeltas(
            aggregateBatchDeltas(pending),
            meanRawPenaltyFactor,
            totalRecipes
          )
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
        else loopWithFactor(totalProcessed, meanRawPenaltyFactor, totalRecipes)
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
         |RETURN e.eventId AS eventId,
         |       e.beforeVector AS beforeVector,
         |       e.afterVector AS afterVector,
         |       e.attempts AS attempts
         |""".stripMargin,
      Map(
        "maxAttempts" -> Int.box(maxAttempts),
        "limit" -> Int.box(limit)
      ),
      (result: org.neo4j.driver.Result) =>
        result.asScala
          .map(record => {
            val eventId = record.get("eventId").asString()
            val beforeVector =
              if (record.get("beforeVector").isNull) Seq.empty
              else fromJsonVector(record.get("beforeVector").asString())
            val afterVector =
              if (record.get("afterVector").isNull) Seq.empty
              else fromJsonVector(record.get("afterVector").asString())
            val attempts = record.get("attempts").asInt()
            PendingEvent(eventId, beforeVector, afterVector, attempts)
          })
          .toSeq
    )

  private def fromJsonVector(json: String): Seq[IngredientVectorItem] =
    decode[Seq[IngredientVectorItem]](Option(json).getOrElse("[]"))
      .getOrElse(Seq.empty)

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

  private def aggregateBatchDeltas(
      events: Seq[PendingEvent]
  ): Seq[IngredientDelta] =
    events
      .flatMap(event => deltasFrom(event.beforeVector, event.afterVector))
      .groupBy(_.ingredientId)
      .map { case (ingredientId, ingredientDeltas) =>
        IngredientDelta(
          ingredientId = ingredientId,
          recipeCountDelta = ingredientDeltas.map(_.recipeCountDelta).sum,
          rawDelta = ingredientDeltas.map(_.rawDelta).sum
        )
      }
      .filter(delta =>
        delta.recipeCountDelta != 0 || math.abs(delta.rawDelta) > 1e-12
      )
      .toSeq

  private def deltasFrom(
      beforeVector: Seq[IngredientVectorItem],
      afterVector: Seq[IngredientVectorItem]
  ): Seq[IngredientDelta] = {
    val beforeById = beforeVector.map(item => item.ingredientId -> item).toMap
    val afterById = afterVector.map(item => item.ingredientId -> item).toMap
    val allIds = (beforeById.keySet ++ afterById.keySet).toSeq
    allIds
      .map(ingredientId => {
        val beforeWeight =
          beforeById.get(ingredientId).map(_.rawNormalizedWeight).getOrElse(0.0)
        val afterWeight =
          afterById.get(ingredientId).map(_.rawNormalizedWeight).getOrElse(0.0)
        val inBefore = beforeById.contains(ingredientId)
        val inAfter = afterById.contains(ingredientId)
        val recipeCountDelta =
          if (inBefore && !inAfter) -1
          else if (!inBefore && inAfter) 1
          else 0
        IngredientDelta(
          ingredientId = ingredientId,
          recipeCountDelta = recipeCountDelta,
          rawDelta = afterWeight - beforeWeight
        )
      })
      .filter(delta =>
        delta.recipeCountDelta != 0 || math.abs(delta.rawDelta) > 1e-12
      )
  }

  private def applyIngredientDeltas(
      deltas: Seq[IngredientDelta],
      meanRawPenaltyFactor: Double,
      totalRecipes: Int
  ): Task[Unit] =
    if (deltas.isEmpty) ZIO.unit
    else
      database.writeTransaction(
        s"""
           |UNWIND $$deltas AS delta
           |MATCH (ingredient:Ingredient {id: delta.ingredientId})
           |WITH ingredient, $$totalRecipes AS totalRecipes,
           |     toInteger(delta.recipeCountDelta) AS recipeCountDelta,
           |     toFloat(delta.rawDelta) AS rawDelta
           |WITH ingredient, totalRecipes,
           |     CASE
           |       WHEN coalesce(ingredient.recipeCount, 0) + recipeCountDelta < 0 THEN 0
           |       ELSE coalesce(ingredient.recipeCount, 0) + recipeCountDelta
           |     END AS recipeCount,
           |     coalesce(ingredient.sumRawNormalizedWeight, 0.0) + rawDelta AS sumRawNormalizedWeight
           |${IngredientWeightJobSupport.applyWeightStatsCypher}
           |""".stripMargin,
        Map(
          "deltas" -> deltas
            .map(delta =>
              Map(
                "ingredientId" -> delta.ingredientId,
                "recipeCountDelta" -> Int.box(delta.recipeCountDelta),
                "rawDelta" -> Double.box(delta.rawDelta)
              ).asJava
            )
            .asJava,
          "meanRawPenaltyFactor" -> Double.box(meanRawPenaltyFactor),
          "totalRecipes" -> Int.box(totalRecipes)
        ),
        (_: org.neo4j.driver.Result) => ()
      )

}
