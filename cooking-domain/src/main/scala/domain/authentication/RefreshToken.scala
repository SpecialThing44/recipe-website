package domain.authentication

import domain.shared.Identified
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.time.Instant
import java.util.UUID

case class RefreshToken(
    id: UUID,
    userId: UUID,
    token: String,
    expiresAt: Instant,
    createdAt: Instant,
    isRevoked: Boolean = false
) extends Identified

object RefreshToken {
  implicit val encoder: Encoder[RefreshToken] = deriveEncoder[RefreshToken]
  implicit val decoder: Decoder[RefreshToken] = deriveDecoder[RefreshToken]
}
