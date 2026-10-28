package api.recipes

import domain.recipes.{Recipe, RecipeUpdateInput}
import domain.users.{AvatarUrls, User, UserUpdateInput}
import api.users.UserAdapter
import java.time.Instant
import java.util.UUID
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RecipeAdapterSpec extends AnyFlatSpec with Matchers {
  private val author = User.empty()
  private val image = AvatarUrls("small", "medium", "large")
  private val original = Recipe(
    "Soup",
    author,
    Seq.empty,
    Seq.empty,
    prepTime = 0,
    cookTime = 0,
    servings = 1,
    countryOfOrigin = Some("Canada"),
    public = true,
    wikiLink = Some("https://en.wikipedia.org/wiki/Soup"),
    instructions = "",
    image = Some(image),
    createdOn = Instant.now(),
    updatedOn = Instant.now(),
    id = UUID.randomUUID()
  )

  it should "preserve omitted optional fields and explicitly remove images" in {
    RecipeAdapter
      .adaptUpdate(RecipeUpdateInput(), original, None, None)
      .image shouldBe Some(image)
    RecipeAdapter
      .adaptUpdate(RecipeUpdateInput(removeImage = true), original, None, None)
      .image shouldBe None
  }

  it should "clear optional text when an empty value is supplied" in {
    val updated = RecipeAdapter.adaptUpdate(
      RecipeUpdateInput(countryOfOrigin = Some(""), wikiLink = Some("")),
      original,
      None,
      None
    )
    updated.countryOfOrigin shouldBe None
    updated.wikiLink shouldBe None
  }

  it should "clear avatars and country without changing omitted values" in {
    val user =
      author.copy(avatar = Some(image), countryOfOrigin = Some("Canada"))
    UserAdapter.adaptUpdate(UserUpdateInput(), user).avatar shouldBe Some(image)
    val updated = UserAdapter.adaptUpdate(
      UserUpdateInput(removeAvatar = true, countryOfOrigin = Some("")),
      user
    )
    updated.avatar shouldBe None
    updated.countryOfOrigin shouldBe None
  }
}
