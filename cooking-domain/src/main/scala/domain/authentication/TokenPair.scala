package domain.authentication

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class TokenPair(
    accessToken: String,
    refreshToken: String
)

object TokenPair {
  implicit val encoder: Encoder[TokenPair] = deriveEncoder[TokenPair]
  implicit val decoder: Decoder[TokenPair] = deriveDecoder[TokenPair]
}
