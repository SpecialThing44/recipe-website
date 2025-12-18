package integration

import domain.filters.{Filters, StringFilter}
import domain.users.{User, UserInput, UserUpdateInput}
import org.scalatest.matchers.should.Matchers.shouldBe

import scala.language.{implicitConversions, strictEquality}

class UserIntegrationTest extends IntegrationTestFramework {
  val johnDoeUserInput = UserInput(
    name = "John Doe",
    email = "john@example.com"
  )

  val janeDoeUserInput = UserInput(
    name = "Jane Doe",
    email = "jane@example.com"
  )

  val bobSmithUserInput = UserInput(
    name = "Bob Smith",
    email = "bob@example.com"
  )

  def usersMatch(user1: User, user2: User): Unit = {
    user1.name shouldBe user2.name
    user1.email shouldBe user2.email
    user1.id shouldBe user2.id
    if (user1.countryOfOrigin.isDefined)
      user1.countryOfOrigin shouldBe user2.countryOfOrigin
  }

  def userInputsMatch(userInput: UserInput, user: User): Unit = {
    user.name shouldBe userInput.name
    user.email shouldBe userInput.email
    user.countryOfOrigin shouldBe userInput.countryOfOrigin
  }

  def userUpdatesMatch(userUpdateInput: UserUpdateInput, user: User): Unit = {
    userUpdateInput.name.map(name => user.name shouldBe name)
    userUpdateInput.email.map(email => user.email shouldBe email)
    user.countryOfOrigin shouldBe userUpdateInput.countryOfOrigin
  }

  it should "create a user and get it by ID" in {
    val user = createTestUser(standardUserInput)
    userInputsMatch(standardUserInput, user)
    val retrievedUser = getUserById(user.id)
    usersMatch(user.copy(email = ""), retrievedUser)
  }

  it should "update a user" in {
    val user = createTestUser(
      standardUserInput
    )
    login(user.id)
    val updateInput = UserUpdateInput(
      name = Some("Updated User Name"),
      email = Some("updated-email@example.com"),
      countryOfOrigin = Some("USA")
    )

    val updatedUser = updateUser(user, updateInput)
    userUpdatesMatch(updateInput, updatedUser)

    val retrievedUser = getUserById(user.id)
    usersMatch(updatedUser, retrievedUser)
  }

  it should "fetch users with filters" in {
    val user1 = createTestUser(johnDoeUserInput)
    val user2 = createTestUser(janeDoeUserInput)
    val user3 = createTestUser(bobSmithUserInput)

    val idFilter = Filters.empty().copy(id = Some(user1.id))
    val idFilterResults = listUsers(idFilter)
    idFilterResults.length shouldBe 1
    idFilterResults.head.id shouldBe user1.id

    val idsFilter = Filters.empty().copy(ids = Some(List(user1.id, user2.id)))
    val idsFilterResults = listUsers(idsFilter)
    idsFilterResults.length shouldBe 2
    idsFilterResults.map(_.id) should contain allOf (user1.id, user2.id)

    val nameFilter = Filters
      .empty()
      .copy(
        name = Some(
          StringFilter
            .empty()
            .copy(
              equals = Some(janeDoeUserInput.name.toLowerCase)
            )
        )
      )
    val nameFilterResults = listUsers(nameFilter)
    nameFilterResults.length shouldBe 1
    nameFilterResults.head.name shouldBe "Jane Doe"

    val nameContainsFilter = Filters
      .empty()
      .copy(
        name =
          Some(StringFilter(None, None, contains = Some("doe"), None, None))
      )
    val nameContainsResults = listUsers(nameContainsFilter)
    nameContainsResults.length shouldBe 2
    nameContainsResults.map(_.name) should contain allOf (
      johnDoeUserInput.name,
      janeDoeUserInput.name
    )

    val emailFilter = Filters
      .empty()
      .copy(
        email = Some(
          StringFilter
            .empty()
            .copy(
              equals = Some(bobSmithUserInput.email),
            )
        )
      )
    val emailFilterResults = listUsers(emailFilter)
    emailFilterResults.length shouldBe 1
    emailFilterResults.head.email shouldBe ""
  }

  it should "delete a user" in {
    val user = createTestUser(standardUserInput)

    val retrievedUser = getUserById(user.id)
    retrievedUser.id shouldBe user.id

    login(user.id)

    val deletedUser = deleteUser(user.id)
    deletedUser.id shouldBe user.id

    val updatedDeletedUser = getUserById(user.id)
    updatedDeletedUser.id shouldBe user.id
    updatedDeletedUser.name shouldBe "Deleted User"
  }
}
