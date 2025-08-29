package domain.filters

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final case class SimilarityFilter(
    alpha: Double,
    beta: Double,
    gamma: Double,
    minScore: Double
)

object SimilarityFilter {
  implicit val encoder: Encoder[SimilarityFilter] = deriveEncoder[SimilarityFilter]
  implicit val decoder: Decoder[SimilarityFilter] = deriveDecoder[SimilarityFilter]
}
