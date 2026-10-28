package api.users

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.google.inject.Inject
import context.ApiContext
import domain.types.{AuthenticationError, AuthorizationError}
import domain.users.User
import persistence.users.Users
import play.api.Configuration
import zio.ZIO

import java.net.URI
import java.util.concurrent.TimeUnit
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.UUID

@com.google.inject.Singleton
class AuthenticationInteractor @Inject() (
    config: Configuration,
    userPersistence: Users
) {
  private val issuer = config.get[String]("auth.issuer")
  private val audience = config.get[String]("auth.audience")
  private val jwksUrl = config.get[String]("auth.jwksUrl")
  private lazy val jwkProvider =
    new JwkProviderBuilder(URI.create(jwksUrl).toURL)
      .cached(10, 24, TimeUnit.HOURS)
      .rateLimited(10, 1, TimeUnit.MINUTES)
      .timeouts(3000, 3000)
      .build()

  def validateAuthentikToken(
      token: String
  ): ZIO[ApiContext, Throwable, User] = {
    ZIO
      .attemptBlocking {
        val decodedToken = JWT.decode(token)
        val jwk = jwkProvider.get(decodedToken.getKeyId)
        verifyToken(token, jwk.getPublicKey.asInstanceOf[RSAPublicKey])
      }
      .flatMap { decodedJwt =>
        val identity = decodedJwt.getSubject
        val email = decodedJwt.getClaim("email").asString()
        val name = decodedJwt.getClaim("preferred_username").asString()
        ensureUserExists(identity, email, name)
      }
  }

  private[users] def verifyToken(
      token: String,
      publicKey: RSAPublicKey
  ): com.auth0.jwt.interfaces.DecodedJWT = {
    val verified = JWT
      .require(Algorithm.RSA256(publicKey, null))
      .withIssuer(issuer)
      .withAudience(audience)
      .withClaimPresence("exp")
      .build()
      .verify(token)
    val requiredClaims = Seq(
      verified.getSubject,
      verified.getClaim("email").asString(),
      verified.getClaim("preferred_username").asString()
    )
    if (requiredClaims.exists(value => value == null || value.trim.isEmpty))
      throw AuthenticationError("Token is missing required identity claims")
    verified
  }

  private def ensureUserExists(
      identity: String,
      email: String,
      name: String
  ): ZIO[ApiContext, Throwable, User] = {
    for {
      maybeUser <- userPersistence.getByIdentity(identity)
      user <- maybeUser match {
        case Some(u) => ZIO.succeed(u)
        case None    =>
          val newUser = User(
            name = name,
            email = email,
            identity = identity,
            createdOn = Instant.now(),
            updatedOn = Instant.now(),
            id = UUID.randomUUID()
          )
          userPersistence.create(newUser)
      }
    } yield user
  }

  def getMaybeUser(
      bearerToken: Option[String]
  ): ZIO[ApiContext, Throwable, Option[User]] = {
    bearerToken match {
      case Some(token) =>
        validateAuthentikToken(token.stripPrefix("Bearer "))
          .map(Some(_))
          .catchAll(error => {
            println("Error")
            println(error.getMessage)
            println(error.getStackTrace)
            ZIO.succeed(None)
          })
      case None => ZIO.succeed(None)
    }
  }
}

object AuthenticationInteractor {
  def ensureAuthenticatedAndMatchingUser(
      maybeUser: Option[User],
      originalUserId: UUID
  ): ZIO[Any, AuthenticationError | AuthorizationError, Unit] = for {
    user <- ensureIsLoggedIn(maybeUser)
    _ <- ensureUUIDMatch(originalUserId, user.id)
  } yield ()

  private def ensureUUIDMatch(
      originalUserId: UUID,
      loggedInUserId: UUID
  ): ZIO[Any, AuthorizationError, Unit] = if (
    originalUserId == loggedInUserId
  ) {
    ZIO.unit
  } else {
    ZIO.fail(
      AuthorizationError(
        "Cannot update other users or recipes belonging to them"
      )
    )
  }

  def ensureIsLoggedIn(
      maybeUser: Option[User],
  ): ZIO[Any, AuthenticationError, User] = maybeUser match {
    case Some(user) => ZIO.succeed(user)
    case None       =>
      ZIO.fail(AuthenticationError("Cannot update if not logged in"))
  }

  def ensureIsAdmin(
      user: User
  ): ZIO[Any, AuthorizationError, Unit] = {
    if (user.admin) {
      ZIO.unit
    } else {
      ZIO.fail(
        AuthorizationError("Admin privileges required for this operation")
      )
    }
  }
}
