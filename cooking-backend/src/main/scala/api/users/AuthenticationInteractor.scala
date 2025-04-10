package api.users

import com.google.inject.Inject
import context.ApiContext
import domain.people.users.User
import domain.types.AuthenticationError
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
import scala.util.{Failure, Success, Try}

class AuthenticationInteractor @Inject() (
    persistence: Users,
) {
  private val secretKey = "secrets"

  def getMaybeUser(
      bearerToken: Option[String],
  ): ZIO[ApiContext, Throwable, Option[User]] =
    bearerToken match {
      case Some(token) if !TokenStore.isTokenBlacklisted(token) =>
        ZIO
          .fromTry(
            Try(Jwt.decode(token.trim, secretKey, Seq(JwtAlgorithm.HS256)))
          )
          .flatMap {
            case claim if claim.get.isValid(Clock.systemUTC()) =>
              decode[User](claim.get.content) match {
                case Right(user) => ZIO.succeed(Some(user))
                case Left(error) =>
                  ZIO.fail(
                    new IllegalArgumentException(
                      s"Failed to decode user: ${error.getMessage}"
                    )
                  )
              }
            case _ => ZIO.succeed(None) // Token is expired
          }
          .catchAll(error =>
            ZIO.fail(
              new IllegalArgumentException(
                s"Failed to decode token: ${error.getMessage}"
              )
            )
          )
      case _ => ZIO.succeed(None)
    }

  def signup(user: User): ZIO[ApiContext, Throwable, String] = {
    val hashedPassword = SecureHash.createHash(user.password)
    val userWithHashedPassword = user.copy(password = hashedPassword)
    for {
      newUserZio <- persistence.create(userWithHashedPassword)
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
      user <- persistence.authenticate(email)
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
              TokenStore.blacklistToken(
                token,
                Instant.ofEpochSecond(expiration)
              )
              ZIO.succeed(true)
            case Failure(_) => ZIO.succeed(false)
          }
        case None => ZIO.succeed(false)
      }
    } yield result

  def ensureAuthenticated(
      maybeUser: Option[User],
      originalEntityId: Option[UUID]
  ): ZIO[Any, AuthenticationError, Unit] = {
    for {
      _ <- ensureIsLoggedIn(maybeUser)
      _ <- ZIO
        .fail(AuthenticationError("Cannot update if not logged in"))
        .when(maybeUser.map(_.id) != originalEntityId)
    } yield ()
  }

  def ensureIsLoggedIn(
      maybeUser: Option[User],
  ): ZIO[Any, AuthenticationError, Option[Nothing]] = ZIO
    .fail(AuthenticationError("Cannot update if not logged in"))
    .when(maybeUser.isEmpty)
}
