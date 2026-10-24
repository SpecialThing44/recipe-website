package http

import domain.recipes.RecipeUpdateInput
import domain.users.UserUpdateInput
import io.circe.parser.decode
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class UpdatePayloadCompatibilitySpec extends AnyFlatSpec with Matchers {
  it should "accept recipe update payloads that omit the image removal flag" in {
    decode[RecipeUpdateInput]("""{"name":"Soup"}""") shouldBe
      Right(RecipeUpdateInput(name = Some("Soup")))
    decode[RecipeUpdateInput]("{}") shouldBe Right(RecipeUpdateInput())
  }

  it should "accept profile update payloads that omit the avatar removal flag" in {
    decode[UserUpdateInput]("""{"name":"Cook"}""") shouldBe
      Right(UserUpdateInput(name = Some("Cook")))
    decode[UserUpdateInput]("{}") shouldBe Right(UserUpdateInput())
  }

  it should "preserve explicit image and avatar removal requests" in {
    decode[RecipeUpdateInput]("""{"removeImage":true}""") shouldBe
      Right(RecipeUpdateInput(removeImage = true))
    decode[UserUpdateInput]("""{"removeAvatar":true}""") shouldBe
      Right(UserUpdateInput(removeAvatar = true))
  }

  it should "reject malformed removal flags" in {
    decode[RecipeUpdateInput]("""{"removeImage":"true"}""").isLeft shouldBe true
    decode[UserUpdateInput]("""{"removeAvatar":"true"}""").isLeft shouldBe true
  }
}
