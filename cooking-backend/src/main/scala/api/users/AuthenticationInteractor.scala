package api.users

import com.google.inject.Inject
import context.ApiContext
import domain.people.users.User
import io.circe.jawn.decode
import io.circe.syntax.*
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import persistence.users.Users
import play.api.mvc.Request
import zio.ZIO

import java.time.{Clock, Instant}
import scala.util.{Failure, Success, Try}

class AuthenticationInteractor @Inject() (
    persistence: Users,
) {
  private val secretKey = ""

  def getMaybeUser(
      bearerToken: Option[String],
  ): ZIO[ApiContext, Throwable, Option[User]] =
    bearerToken match {
      case Some(token) if !TokenStore.isTokenBlacklisted(token) =>
        Try(Jwt.decode(token, secretKey, Seq(JwtAlgorithm.HS256))) match {
          case Success(claim) =>
            if (claim.get.isValid(Clock.systemUTC())) {
              decode[User](claim.get.content) match {
                case Right(user) => ZIO.succeed(Some(user))
                case Left(_)     => ZIO.succeed(None)
              }
            } else {
              ZIO.succeed(None)
            }
          case Failure(_) => ZIO.succeed(None)
        }
      case _ => ZIO.succeed(None)
    }

  def signup(user: User): ZIO[ApiContext, Throwable, String] =
    for {
      newUserZio <- persistence.create(user)
      claim = JwtClaim(
        content = user.asJson.noSpaces,
        expiration = Some(Instant.now.plusSeconds(3600).getEpochSecond),
        issuedAt = Some(Instant.now.getEpochSecond)
      )
      token: String = Jwt.encode(claim, secretKey, JwtAlgorithm.HS256)
    } yield token

  def login(
      email: String,
      password: String,
  ): ZIO[ApiContext, Throwable, Option[String]] = {
    for {
      maybeUser <- persistence.authenticate(email, password)
      token <- maybeUser match {
        case Some(user) =>
          val claim = JwtClaim(
            content = user.asJson.noSpaces,
            expiration = Some(Instant.now.plusSeconds(3600).getEpochSecond),
            issuedAt = Some(Instant.now.getEpochSecond)
          )
          ZIO.succeed(Some(Jwt.encode(claim, secretKey, JwtAlgorithm.HS256)))
        case None => ZIO.succeed(None)
      }
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

}
