package api.users

import java.time.Instant
import scala.collection.concurrent.TrieMap

object TokenStore {
  private val blacklistedTokens = TrieMap[String, Instant]()

  def blacklistToken(token: String, expiration: Instant): Option[Instant] = {
    blacklistedTokens.put(token, expiration)
  }

  def isTokenBlacklisted(token: String): Boolean = {
    blacklistedTokens.get(token) match {
      case Some(expiration) if expiration.isAfter(Instant.now) => true
      case _ =>
        blacklistedTokens.remove(token)
        false
    }
  }
}
