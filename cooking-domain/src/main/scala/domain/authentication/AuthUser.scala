package domain.authentication

import domain.shared.Identified
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.time.Instant
import java.util.UUID

case class AuthUser(
    id: UUID,
    passwordHash: String,
) extends Identified {}

object AuthUser {
  implicit val encoder: Encoder[AuthUser] = deriveEncoder[AuthUser]
  implicit val decoder: Decoder[AuthUser] = deriveDecoder[AuthUser]
}
