package persistence.ingredients.weights

import com.google.inject.{Inject, Singleton}
import domain.ingredients.Unit as IngredientUnit
import domain.recipes.Recipe
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.EncoderOps
import io.circe.Encoder
import persistence.neo4j.Database
import zio.Task

import java.util.UUID

@Singleton
class IngredientWeightEventInteractor @Inject()(
    database: Database
) {
  private case class IngredientVectorItem(
      ingredientId: String,
      rawNormalizedWeight: Double
  )

  private given Encoder[IngredientVectorItem] =
    deriveEncoder[IngredientVectorItem]

  def enqueueRecipeCreated(recipe: Recipe): Task[Unit] =
    enqueueEvent(
      "recipe_created",
      recipe.id,
      beforeVector = Seq.empty,
      afterVector = vectorFromRecipe(recipe)
    )

  def enqueueRecipeUpdated(before: Recipe, after: Recipe): Task[Unit] =
    enqueueEvent(
      "recipe_updated",
      after.id,
      beforeVector = vectorFromRecipe(before),
      afterVector = vectorFromRecipe(after)
    )

  def enqueueRecipeDeleted(recipe: Recipe): Task[Unit] =
    enqueueEvent(
      "recipe_deleted",
      recipe.id,
      beforeVector = vectorFromRecipe(recipe),
      afterVector = Seq.empty
    )

  def markEventDone(eventId: String): Task[Unit] =
    database.writeTransaction(
      s"""
         |MATCH (e:IngredientWeightEvent {eventId: $$eventId})
         |SET e.status = 'done',
         |    e.completedAt = datetime(),
         |    e.completedOn = datetime()
         |RETURN e.eventId AS eventId
         |""".stripMargin,
      Map("eventId" -> eventId),
      (_: org.neo4j.driver.Result) => ()
    )

  def markEventRetryOrFailed(
      eventId: String,
      attempts: Int,
      maxAttempts: Int,
      baseBackoffSeconds: Int,
      error: String
  ): Task[Unit] = {
    val sanitized = Option(error).getOrElse("unknown")
    if (attempts >= maxAttempts) {
      database.writeTransaction(
        s"""
           |MATCH (e:IngredientWeightEvent {eventId: $$eventId})
           |SET e.status = 'failed',
           |    e.lastError = $$lastError,
           |    e.failedAt = datetime(),
           |    e.failedOn = datetime()
           |RETURN e.eventId AS eventId
           |""".stripMargin,
        Map(
          "eventId" -> eventId,
          "lastError" -> sanitized
        ),
        (_: org.neo4j.driver.Result) => ()
      )
    } else {
      val backoffSeconds =
        baseBackoffSeconds * math.pow(2.0, attempts.toDouble - 1.0).toInt
      database.writeTransaction(
        s"""
           |MATCH (e:IngredientWeightEvent {eventId: $$eventId})
           |SET e.status = 'retry',
           |    e.lastError = $$lastError,
           |    e.nextEligibleAt = datetime() + duration({seconds: $$backoffSeconds}),
           |    e.nextRetryOn = datetime() + duration({seconds: $$backoffSeconds})
           |RETURN e.eventId AS eventId
           |""".stripMargin,
        Map(
          "eventId" -> eventId,
          "lastError" -> sanitized,
          "backoffSeconds" -> Int.box(backoffSeconds)
        ),
        (_: org.neo4j.driver.Result) => ()
      )
    }
  }

  private def vectorFromRecipe(recipe: Recipe): Seq[IngredientVectorItem] = {
    val ingredientWeights = recipe.ingredients.map(instructionIngredient =>
      (
        instructionIngredient.ingredient.id.toString,
        IngredientUnit.toStandardizedAmount(
          instructionIngredient.quantity.unit,
          instructionIngredient.quantity.amount
        ).toDouble
      )
    )
    val totalStandardized = ingredientWeights.map(_._2).sum
    ingredientWeights
      .map { case (ingredientId, standardizedAmount) =>
        val rawNormalizedWeight =
          if (totalStandardized == 0) 0.0
          else standardizedAmount / totalStandardized
        IngredientVectorItem(ingredientId, rawNormalizedWeight)
      }
      .groupBy(_.ingredientId)
      .map { case (ingredientId, entries) =>
        IngredientVectorItem(
          ingredientId,
          entries.map(_.rawNormalizedWeight).sum
        )
      }
      .toSeq
  }

  private def enqueueEvent(
      eventType: String,
      recipeId: UUID,
      beforeVector: Seq[IngredientVectorItem],
      afterVector: Seq[IngredientVectorItem]
  ): Task[Unit] = {
    val eventId = UUID.randomUUID().toString
    val beforeVectorJson = beforeVector.asJson.noSpaces.replace("'", "")
    val afterVectorJson = afterVector.asJson.noSpaces.replace("'", "")
    database.writeTransaction(
      s"""
         |CREATE (e:IngredientWeightEvent {
         |  eventId: $$eventId,
         |  eventType: $$eventType,
         |  recipeId: $$recipeId,
         |  beforeVector: $$beforeVector,
         |  afterVector: $$afterVector,
         |  createdAt: datetime(),
         |  status: 'pending',
         |  attempts: 0
         |})
         |RETURN e.eventId AS eventId
         |""".stripMargin,
      Map(
        "eventId" -> eventId,
        "eventType" -> eventType,
        "recipeId" -> recipeId.toString,
        "beforeVector" -> beforeVectorJson,
        "afterVector" -> afterVectorJson
      ),
      (_: org.neo4j.driver.Result) => ()
    )
  }
}