package integration.support

import api.users.UserFacade
import context.ApiContext
import domain.filters.Filters
import domain.users.{User, UserInput, UserUpdateInput}
import play.api.mvc.Headers
import play.api.test.FakeRequest
import zio.{Runtime, Unsafe, ZLayer}

import java.util.UUID
import scala.collection.mutable.ListBuffer

trait IntegrationUserSupport {
  protected val userFacade: UserFacade
  protected val createdUsers: ListBuffer[UUID] =
    collection.mutable.ListBuffer.empty[UUID]
  protected var loggedInUser: Option[User] = None

  val standardUserInput: UserInput = UserInput(
    name = "Test User",
    email = "test@example.com",
    password = "password123"
  )

  protected def createApiContext(): ZLayer[Any, Nothing, ApiContext]

  protected def createTestUser(userInput: UserInput): User = {
    val user = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          userFacade.create(userInput).provideLayer(createApiContext())
        )
        .getOrThrow()
    }

    createdUsers += user.id
    user
  }

  protected def login(userId: UUID): User = {
    val user = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          userFacade.getById(userId).provideLayer(createApiContext())
        )
        .getOrThrow()
    }
    loggedInUser = Some(user)
    user
  }

  protected def logout(): Unit = {
    loggedInUser = None
  }

  protected def updateUser(user: User, updateInput: UserUpdateInput): User = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          userFacade.update(updateInput, user).provideLayer(createApiContext())
        )
        .getOrThrow()
    }
  }

  protected def listUsers(filters: Filters): Seq[User] = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          userFacade.list(filters).provideLayer(createApiContext())
        )
        .getOrThrow()
    }
  }

  protected def deleteUser(userId: UUID): User = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          userFacade.delete(userId).provideLayer(createApiContext())
        )
        .getOrThrow()
    }
  }

  protected def getUserById(userId: UUID): User = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          userFacade.getById(userId).provideLayer(createApiContext())
        )
        .getOrThrow()
    }
  }

  def loginUser(email: String, password: String): Option[String] = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          userFacade
            .login(email, password)
            .provideLayer(createApiContext())
        )
        .getOrThrow()
    }
  }

  protected def authenticateUser(token: String): Option[User] = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          userFacade
            .authenticate(Some(s"$token"))
            .provideLayer(createApiContext())
        )
        .getOrThrow()
    }
  }

  protected def logoutUser(token: String): Boolean = {
    val request = FakeRequest().withHeaders(
      Headers("Authorization" -> s"$token")
    )

    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          userFacade
            .logout(request)
            .provideLayer(createApiContext())
        )
        .getOrThrow()
    }
  }

  protected def signupUser(userInput: UserInput): String = {
    val token = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          userFacade
            .signup(userInput)
            .provideLayer(createApiContext())
        )
        .getOrThrow()
    }
    val user = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          userFacade.authenticate(Some(token)).provideLayer(createApiContext())
        )
        .getOrThrow()
    }
    createdUsers += user.get.id
    token
  }

}
