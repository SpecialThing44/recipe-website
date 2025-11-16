package persistence.authentication

import com.google.inject.ImplementedBy
import context.ApiContext
import domain.authentication.RefreshToken
import zio.ZIO

import java.util.UUID

@ImplementedBy(classOf[RefreshTokensPersistence])
trait RefreshTokens {
  def create(token: RefreshToken): ZIO[ApiContext, Throwable, RefreshToken]
  def getByToken(token: String): ZIO[ApiContext, Throwable, Option[RefreshToken]]
  def revokeToken(tokenId: UUID): ZIO[ApiContext, Throwable, Unit]
  def revokeAllForUser(userId: UUID): ZIO[ApiContext, Throwable, Unit]
  def deleteAllForUser(userId: UUID): ZIO[ApiContext, Throwable, Unit]
  def deleteExpired(): ZIO[ApiContext, Throwable, Unit]
}
