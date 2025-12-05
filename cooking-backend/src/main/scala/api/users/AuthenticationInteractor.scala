package api.users

import com.auth0.jwk.UrlJwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.google.inject.Inject
import context.ApiContext
import domain.types.AuthenticationError
import domain.users.User
import persistence.users.Users
import play.api.Configuration
import zio.ZIO

import java.net.URL
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.UUID

class AuthenticationInteractor @Inject() (
    config: Configuration,
    userPersistence: Users
) {
  private val issuer = config.get[String]("auth.issuer")
  private val jwksUrl = config.get[String]("auth.jwksUrl")

  def validateAuthentikToken(
      token: String
  ): ZIO[ApiContext, Throwable, User] = {
    ZIO
      .attempt {
        val jwkProvider = new UrlJwkProvider(new URL(jwksUrl))
        val decodedToken = JWT.decode(token)
        val jwk = jwkProvider.get(decodedToken.getKeyId)
        val algorithm =
          Algorithm.RSA256(jwk.getPublicKey.asInstanceOf[RSAPublicKey], null)

        JWT
          .require(algorithm)
          .withIssuer(issuer)
          .build()
          .verify(token)
      }
      .flatMap { decodedJwt =>
        val identity = decodedJwt.getSubject
        val email = decodedJwt.getClaim("email").asString()
        val name = decodedJwt.getClaim("preferred_username").asString()
        ensureUserExists(identity, email, name)
      }
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
        case None =>
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
    println("BearerToken1")
    bearerToken.map(token => println(token))
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
  ): ZIO[Any, AuthenticationError, Unit] = for {
    user <- ensureIsLoggedIn(maybeUser)
    _ <- ensureUUIDMatch(originalUserId, user.id)
  } yield ()

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

  def ensureIsAdmin(
      user: User
  ): ZIO[Any, AuthenticationError, Unit] = {
    if (user.admin) {
      ZIO.unit
    } else {
      ZIO.fail(
        AuthenticationError("Admin privileges required for this operation")
      )
    }
  }
}
