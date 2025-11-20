package integration

import org.scalatest.matchers.should.Matchers.shouldBe

import scala.language.implicitConversions
import scala.language.strictEquality

class AuthenticationIntegrationTest extends IntegrationTestFramework {
  it should "login a user with correct credentials and return a token" in {
    val user = createTestUser(standardUserInput)

    val token = loginUser(standardUserInput.email, standardUserInput.password)

    token shouldBe defined
  }

  it should "authenticate a user with a valid token" in {
    val authenticationUserInput = standardUserInput.copy(email = "authentication-test@mail.com")
    val user = createTestUser(authenticationUserInput)

    val tokenOption = loginUser(authenticationUserInput.email, authenticationUserInput.password)

    tokenOption shouldBe defined
    val token = tokenOption.get

    val authenticatedUser = authenticateUser(token.accessToken)

    authenticatedUser shouldBe defined
    authenticatedUser.get.id shouldBe user.id
    authenticatedUser.get.email shouldBe user.email
    authenticatedUser.get.name shouldBe user.name
  }

  it should "logout a user with a valid token" in {
    val user = createTestUser(standardUserInput)

    val tokenOption = loginUser(standardUserInput.email, standardUserInput.password)

    tokenOption shouldBe defined
    val token = tokenOption.get

    val logoutResult = logoutUser(token.accessToken)

    logoutResult shouldBe true

    val authenticatedUser = authenticateUser(token.accessToken)

    authenticatedUser shouldBe None
  }

  it should "signup a new user and return a valid token" in {
    val token = signupUser(standardUserInput)

    token.accessToken shouldBe a[String]

    val authenticatedUser = authenticateUser(token.accessToken)

    authenticatedUser shouldBe defined
    authenticatedUser.get.name shouldBe standardUserInput.name
    authenticatedUser.get.email shouldBe standardUserInput.email
  }
}
