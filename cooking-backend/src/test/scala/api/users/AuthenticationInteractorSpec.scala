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
  it should "reuse the cached signing key across authenticated requests" in {
    val requests = new java.util.concurrent.atomic.AtomicInteger()
    val server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0)
    def base64(number: java.math.BigInteger): String = {
      val bytes = number.toByteArray.dropWhile(_ == 0.toByte)
      java.util.Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)
    }
    val jwks = s"""{"keys":[{"kty":"RSA","kid":"test-key","alg":"RS256","use":"sig","n":"${base64(publicKey.getModulus)}","e":"${base64(publicKey.getPublicExponent)}"}]}"""
    server.createContext("/jwks", exchange => {
      requests.incrementAndGet()
      val bytes = jwks.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      exchange.sendResponseHeaders(200, bytes.length)
      exchange.getResponseBody.write(bytes)
      exchange.close()
    })
    server.start()
    try {
      val users = mock(classOf[Users])
      org.mockito.Mockito.when(users.getByIdentity("user-identity")).thenReturn(zio.ZIO.succeed(Some(domain.users.User.empty())))
      val cached = new AuthenticationInteractor(Configuration.from(Map(
        "auth.issuer" -> "https://issuer.example/", "auth.audience" -> "recipe-api",
        "auth.jwksUrl" -> s"http://127.0.0.1:${server.getAddress.getPort}/jwks"
      )), users)
      val signed = JWT.create().withKeyId("test-key").withIssuer("https://issuer.example/").withAudience("recipe-api")
        .withSubject("user-identity").withClaim("email", "user@example.com").withClaim("preferred_username", "User")
        .withExpiresAt(Instant.now().plusSeconds(60)).sign(algorithm)
      zio.Unsafe.unsafe { implicit unsafe =>
        zio.Runtime.default.unsafe.run(
          (cached.validateAuthentikToken(signed) *> cached.validateAuthentikToken(signed))
            .provideEnvironment(zio.ZEnvironment(context.ApiContext(null, context.ApplicationContext(None))))
        ).getOrThrowFiberFailure()
      }
      requests.get() shouldBe 1
    } finally server.stop(0)
  }

}
