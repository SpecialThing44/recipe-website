package api.moderation

import com.google.inject.{Inject, Singleton}
import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.parser.*
import io.circe.syntax.*
import play.api.Configuration
import sttp.client3.*
import zio.{Task, ZIO}

case class ModerationRequest(input: String)

case class ModerationCategory(
    hate: Boolean,
    `hate/threatening`: Boolean,
    harassment: Boolean,
    `harassment/threatening`: Boolean,
    `self-harm`: Boolean,
    `self-harm/intent`: Boolean,
    `self-harm/instructions`: Boolean,
    sexual: Boolean,
    `sexual/minors`: Boolean,
    violence: Boolean,
    `violence/graphic`: Boolean
)

case class ModerationCategoryScores(
    hate: Double,
    `hate/threatening`: Double,
    harassment: Double,
    `harassment/threatening`: Double,
    `self-harm`: Double,
    `self-harm/intent`: Double,
    `self-harm/instructions`: Double,
    sexual: Double,
    `sexual/minors`: Double,
    violence: Double,
    `violence/graphic`: Double
)

case class ModerationResult(
    flagged: Boolean,
    categories: ModerationCategory,
    category_scores: ModerationCategoryScores
)

case class ModerationResponse(
    id: String,
    model: String,
    results: List[ModerationResult]
)

case class ModerationViolation(
    message: String,
    categories: List[String]
)

object ModerationRequest {
  implicit val encoder: Encoder[ModerationRequest] =
    deriveEncoder[ModerationRequest]
}

object ModerationCategory {
  implicit val decoder: Decoder[ModerationCategory] =
    deriveDecoder[ModerationCategory]
}

object ModerationCategoryScores {
  implicit val decoder: Decoder[ModerationCategoryScores] =
    deriveDecoder[ModerationCategoryScores]
}

object ModerationResult {
  implicit val decoder: Decoder[ModerationResult] =
    deriveDecoder[ModerationResult]
}

object ModerationResponse {
  implicit val decoder: Decoder[ModerationResponse] =
    deriveDecoder[ModerationResponse]
}

@Singleton
class OpenAIModerationClient @Inject() (config: Configuration) {
  private val apiKey = config.get[String]("openai.apiKey")
  private val skipModeration = config.get[Boolean]("openai.skipModeration")
  private val apiUrl = "https://api.openai.com/v1/moderations"
  private val backend = HttpClientSyncBackend()

  /** Moderates text content using OpenAI's moderation API. Returns None if
    * content is acceptable, or Some(ModerationViolation) if flagged.
    */
  def moderateText(text: String): Task[Option[ModerationViolation]] =
    ZIO.attempt {
      if (skipModeration || text.trim.isEmpty) {
        None
      } else {
        val requestBody = ModerationRequest(text).asJson.noSpaces

        val response = basicRequest
          .post(uri"$apiUrl")
          .header("Authorization", s"Bearer $apiKey")
          .header("Content-Type", "application/json")
          .body(requestBody)
          .response(asString)
          .send(backend)

        response.body match {
          case Right(body) =>
            decode[ModerationResponse](body) match {
              case Right(moderationResponse) =>
                moderationResponse.results.headOption match {
                  case Some(result) if result.flagged =>
                    val flaggedCategories =
                      extractFlaggedCategories(result.categories)
                    Some(
                      ModerationViolation(
                        message = buildViolationMessage(flaggedCategories),
                        categories = flaggedCategories
                      )
                    )
                  case _ =>
                    None
                }
              case Left(error) =>
                throw new Exception(
                  s"Failed to parse moderation response: ${error.getMessage}"
                )
            }
          case Left(error) =>
            throw new Exception(s"Moderation API request failed: $error")
        }
      }
    }

  private def extractFlaggedCategories(
      categories: ModerationCategory
  ): List[String] = {
    val categoryMap = Map(
      "hate" -> categories.hate,
      "hate/threatening" -> categories.`hate/threatening`,
      "harassment" -> categories.harassment,
      "harassment/threatening" -> categories.`harassment/threatening`,
      "self-harm" -> categories.`self-harm`,
      "self-harm/intent" -> categories.`self-harm/intent`,
      "self-harm/instructions" -> categories.`self-harm/instructions`,
      "sexual" -> categories.sexual,
      "sexual/minors" -> categories.`sexual/minors`,
      "violence" -> categories.violence,
      "violence/graphic" -> categories.`violence/graphic`
    )

    categoryMap.filter(_._2).keys.toList
  }

  private def buildViolationMessage(categories: List[String]): String = {
    val categoryDescriptions = Map(
      "hate" -> "hateful content",
      "hate/threatening" -> "hateful and threatening content",
      "harassment" -> "harassing content",
      "harassment/threatening" -> "harassing and threatening content",
      "self-harm" -> "self-harm content",
      "self-harm/intent" -> "self-harm intent",
      "self-harm/instructions" -> "self-harm instructions",
      "sexual" -> "sexual content",
      "sexual/minors" -> "content involving minors",
      "violence" -> "violent content",
      "violence/graphic" -> "graphic violent content"
    )

    val descriptions = categories.flatMap(cat => categoryDescriptions.get(cat))

    if (descriptions.isEmpty) {
      "Content violates our community guidelines"
    } else if (descriptions.length == 1) {
      s"Content flagged for ${descriptions.head}"
    } else {
      s"Content flagged for: ${descriptions.mkString(", ")}"
    }
  }
}
