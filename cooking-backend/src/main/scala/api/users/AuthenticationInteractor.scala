package api.users

import com.google.inject.Inject
import context.ApiContext
import domain.types.AuthenticationError
import domain.users.User
import io.circe.jawn.decode
import io.circe.syntax.*
import io.github.nremond.SecureHash
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import persistence.users.Users
import play.api.mvc.Request
import zio.ZIO

import java.time.{Clock, Instant}
import java.util.UUID
import scala.util.{Failure, Success}

class AuthenticationInteractor @Inject() (
    userPersistence: Users,
) {
  private val secretKey = "secrets"

  def getMaybeUser(
      bearerToken: Option[String],
  ): ZIO[ApiContext, Throwable, Option[User]] =
    bearerToken match {
      case Some(token) if !TokenStore.isTokenBlacklisted(token) =>
        ZIO
          .fromTry(
            Jwt.decode(
              token.trim.stripPrefix("Bearer "),
              "secrets",
              Seq(JwtAlgorithm.HS256)
            )
          )
          .flatMap {
            case claim if claim.isValid(Clock.systemUTC()) =>
              decode[User](claim.content) match {
                case Right(user) => ZIO.succeed(Some(user))
                case Left(error) =>
                  ZIO.succeed(None)
              }
            case _ => ZIO.succeed(None) // Token is expired
          }
          .catchAll(error => ZIO.succeed(None))
      case _ => ZIO.succeed(None)
    }

  def signup(user: User): ZIO[ApiContext, Throwable, String] = {
    val hashedPassword = SecureHash.createHash(user.password)
    val userWithHashedPassword = user.copy(password = hashedPassword)
    for {
      newUserZio <- userPersistence.create(userWithHashedPassword)
      claim = JwtClaim(
        content = userWithHashedPassword.asJson.noSpaces,
        expiration = Some(Instant.now.plusSeconds(3600).getEpochSecond),
        issuedAt = Some(Instant.now.getEpochSecond)
      )
      token: String = Jwt.encode(claim, secretKey, JwtAlgorithm.HS256)
    } yield token
  }

  def login(
      email: String,
      password: String,
  ): ZIO[ApiContext, Throwable, Option[String]] = {
    for {
      user <- userPersistence.authenticate(email)
      _ <- ZIO.cond(
        SecureHash.validatePassword(password, user.password),
        (),
        AuthenticationError("Incorrect Password")
      )
      claim = JwtClaim(
        content = user.asJson.noSpaces,
        expiration = Some(Instant.now.plusSeconds(3600).getEpochSecond),
        issuedAt = Some(Instant.now.getEpochSecond)
      )
      token <- ZIO.succeed(
        Some(Jwt.encode(claim, secretKey, JwtAlgorithm.HS256))
      )
    } yield token
  }

  def logout(
      request: Request[?],
  ): ZIO[ApiContext, Throwable, Boolean] =
    for {
      authHeader <- ZIO.succeed(request.headers.get("Authorization"))
      result <- authHeader match {
        case Some(auth) =>
          val bearerToken = OAuth2BearerToken(auth)
          val token = bearerToken.token
          Jwt.decode(token, secretKey, Seq(JwtAlgorithm.HS256)) match {
            case Success(claim) =>
              val expiration: Long =
                claim.expiration.getOrElse(Instant.now.getEpochSecond)
              val _ = TokenStore.blacklistToken(
                token,
                Instant.ofEpochSecond(expiration)
              )
              ZIO.succeed(true)
            case Failure(_) => ZIO.succeed(false)
          }
        case None => ZIO.succeed(false)
      }
    } yield result

}

object AuthenticationInteractor {
  def ensureAuthenticatedAndMatchingUser(
      maybeUser: Option[User],
      originalUserId: UUID
  ): ZIO[Any, AuthenticationError, Unit] = for {
    user <- ensureIsLoggedIn(maybeUser)
  } yield ensureUUIDMatch(originalUserId, user.id)

  private def ensureUUIDMatch(
      originalUserId: UUID,
      loggedInUserId: UUID
  ): ZIO[Any, AuthenticationError, Unit] = if (
    originalUserId == loggedInUserId
  ) {
    ZIO.unit
  } else {
    ZIO.fail(
      AuthenticationError(
        "Cannot update other users or recipes belonging to them"
      )
    )
  }

  def ensureIsLoggedIn(
      maybeUser: Option[User],
  ): ZIO[Any, AuthenticationError, User] = maybeUser match {
    case Some(user) => ZIO.succeed(user)
    case None =>
      ZIO.fail(AuthenticationError("Cannot update if not logged in"))
  }
}
