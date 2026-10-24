package api.users

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import java.security.KeyPairGenerator
import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import java.time.Instant
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.mockito.Mockito.mock
import persistence.users.Users
import play.api.Configuration

class AuthenticationInteractorSpec extends AnyFlatSpec with Matchers {
  private val generator = KeyPairGenerator.getInstance("RSA")
  generator.initialize(2048)
  private val keys = generator.generateKeyPair()
  private val publicKey = keys.getPublic.asInstanceOf[RSAPublicKey]
  private val algorithm = Algorithm.RSA256(publicKey, keys.getPrivate.asInstanceOf[RSAPrivateKey])
  private val interactor = new AuthenticationInteractor(Configuration.from(Map(
    "auth.issuer" -> "https://issuer.example/", "auth.audience" -> "recipe-api", "auth.jwksUrl" -> "https://issuer.example/jwks"
  )), mock(classOf[Users]))

  private def token(audience: String, expires: Instant = Instant.now().plusSeconds(60)): String =
    JWT.create().withIssuer("https://issuer.example/").withAudience(audience)
      .withSubject("user-identity").withClaim("email", "user@example.com")
      .withClaim("preferred_username", "User").withExpiresAt(expires).sign(algorithm)

  it should "accept a valid token for this API" in {
    interactor.verifyToken(token("recipe-api"), publicKey).getSubject shouldBe "user-identity"
  }

  it should "reject tokens for another audience and expired tokens" in {
    intercept[JWTVerificationException](interactor.verifyToken(token("other-api"), publicKey))
    intercept[JWTVerificationException](interactor.verifyToken(token("recipe-api", Instant.now().minusSeconds(60)), publicKey))
  }

  it should "reject tokens missing identity claims" in {
    val incomplete = JWT.create().withIssuer("https://issuer.example/").withAudience("recipe-api")
      .withExpiresAt(Instant.now().plusSeconds(60)).sign(algorithm)
    intercept[domain.types.AuthenticationError](interactor.verifyToken(incomplete, publicKey))
  }
}
