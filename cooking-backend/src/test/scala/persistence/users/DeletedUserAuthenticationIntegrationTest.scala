package persistence.users

import domain.types.AuthenticationError
import domain.users.User
import integration.{IntegrationTestFramework, TestAppHolder}
import persistence.neo4j.Database
import zio.{Runtime, Unsafe}

class DeletedUserAuthenticationIntegrationTest
    extends IntegrationTestFramework {
  private val users = TestAppHolder.application.injector.instanceOf[Users]
  private val database = TestAppHolder.application.injector.instanceOf[Database]

  private def findByIdentity(identity: String): Option[User] =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          users.getByIdentity(identity).provideLayer(createApiContext())
        )
        .getOrThrow()
    }

  it should "revoke administrator privileges and reject authentication after account deletion" in {
    val user = createTestAdminUser(standardUserInput)
    findByIdentity(user.identity).map(_.id) shouldBe Some(user.id)
    login(user.id)

    deleteUser(user.id)

    getUserById(user.id).admin shouldBe false
    intercept[AuthenticationError](findByIdentity(user.identity))
  }

  it should "reject legacy deleted accounts even when their administrator flag remains set" in {
    val user = createTestAdminUser(standardUserInput)
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          database.writeTransaction(
            "MATCH (user:User {id: $userId}) SET user:DeletedUser",
            Map("userId" -> user.id.toString),
            _.consume()
          )
        )
        .getOrThrow()
    }

    intercept[AuthenticationError](findByIdentity(user.identity))
  }
}
