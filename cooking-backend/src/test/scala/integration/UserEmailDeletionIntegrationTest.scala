package integration

import domain.filters.{Filters, StringFilter}
import domain.users.UserInput

class UserEmailDeletionIntegrationTest extends IntegrationTestFramework {
  it should "allow multiple account deletions and release both email indexes" in {
    val firstInput = UserInput("First User", "first@example.com")
    val secondInput = UserInput("Second User", "second@example.com")
    val first = createTestUser(firstInput)
    val second = createTestUser(secondInput)

    login(first.id)
    deleteUser(first.id)
    login(second.id)
    deleteUser(second.id)

    getUserById(first.id).name shouldBe "Deleted User"
    getUserById(second.id).name shouldBe "Deleted User"

    val replacementFirst =
      createTestUser(firstInput.copy(email = "FIRST@example.com"))
    val replacementSecond = createTestUser(secondInput)
    val filters = Filters
      .empty()
      .copy(
        email = Some(
          StringFilter
            .empty()
            .copy(anyOf = Some(Seq(firstInput.email, secondInput.email)))
        )
      )

    listUsers(filters).map(_.id).toSet shouldBe Set(
      replacementFirst.id,
      replacementSecond.id
    )
  }
}
