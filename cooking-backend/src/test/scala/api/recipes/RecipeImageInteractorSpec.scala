package api.recipes

import api.storage.SeaweedFSClient
import context.{ApiContext, ApplicationContext}
import domain.recipes.Recipe
import domain.users.{AvatarUrls, User}
import org.apache.pekko.util.ByteString
import org.mockito.Mockito.{mock, verifyNoInteractions, when}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import persistence.recipes.Recipes
import zio.{Runtime, Unsafe, ZEnvironment, ZIO}
import java.util.UUID

class RecipeImageInteractorSpec extends AnyFlatSpec with Matchers {
  it should "preserve stored images when the replacement cannot be decoded" in {
    val owner = User.empty()
    val recipeId = UUID.randomUUID()
    val recipe = mock(classOf[Recipe])
    when(recipe.createdBy).thenReturn(owner)
    when(recipe.image).thenReturn(Some(AvatarUrls("old-small", "old-medium", "old-large")))
    val recipes = mock(classOf[Recipes])
    when(recipes.getById(recipeId)).thenReturn(ZIO.succeed(recipe))
    val storage = mock(classOf[SeaweedFSClient])
    val updater = mock(classOf[RecipeUpdateInteractor])
    val interactor = new RecipeImageInteractor(recipes, storage, updater)
    val result = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(interactor.uploadImage(recipeId, ByteString("not an image"), "image/png")
        .provideEnvironment(ZEnvironment(ApiContext(null, ApplicationContext(Some(owner))))).either).getOrThrowFiberFailure()
    }
    result.isLeft shouldBe true
    verifyNoInteractions(storage, updater)
  }
}
