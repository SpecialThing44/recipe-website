package persistence.authentication

import domain.authentication.RefreshToken
import persistence.Converter

import java.time.Instant
import java.util
import java.util.UUID

object RefreshTokenConverter extends Converter[RefreshToken] {
  val tokenField = "token"
  val userIdField = "userId"
  val expiresAtField = "expiresAt"
  val isRevokedField = "isRevoked"

  override def toGraph(refreshToken: RefreshToken): Map[String, Object] =
    Map(
      idField -> refreshToken.id.toString,
      userIdField -> refreshToken.userId.toString,
      tokenField -> refreshToken.token,
      expiresAtField -> refreshToken.expiresAt.toString,
      createdOnField -> refreshToken.createdAt.toString,
      isRevokedField -> refreshToken.isRevoked.toString
    )

  override def toDomain(record: util.Map[String, AnyRef]): RefreshToken = RefreshToken(
    id = UUID.fromString(record.get(idField).toString),
    userId = UUID.fromString(record.get(userIdField).toString),
    token = record.get(tokenField).toString,
    expiresAt = Instant.parse(record.get(expiresAtField).toString),
    createdAt = Instant.parse(record.get(createdOnField).toString),
    isRevoked = record.get(isRevokedField).toString.toBoolean
  )
}
