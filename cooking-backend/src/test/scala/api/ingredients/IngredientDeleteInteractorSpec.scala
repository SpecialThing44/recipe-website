package api.ingredients

import context.{ApiContext, ApplicationContext}
import domain.filters.Filters
import domain.ingredients.Ingredient
import domain.recipes.Recipe
import domain.users.User
import domain.types.InputError
import org.mockito.Mockito.{mock, never, verify, when}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import persistence.ingredients.Ingredients
import persistence.recipes.Recipes
import zio.{Runtime, Unsafe, ZEnvironment, ZIO}
import java.util.UUID

class IngredientDeleteInteractorSpec extends AnyFlatSpec with Matchers {
  it should "reject deleting an ingredient used by a recipe" in {
    val owner = User.empty().copy(admin = true)
    val ingredient =
      Ingredient("Salt", Seq.empty, "", Seq.empty, owner, UUID.randomUUID())
    val ingredients = mock(classOf[Ingredients])
    val recipes = mock(classOf[Recipes])
    when(ingredients.getById(ingredient.id)).thenReturn(ZIO.succeed(ingredient))
    when(recipes.list(Filters.empty().copy(ingredients = Some(Seq("Salt")))))
      .thenReturn(ZIO.succeed(Seq(mock(classOf[Recipe]))))
    val result = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          new IngredientDeleteInteractor(ingredients, recipes)
            .delete(ingredient.id)
            .provideEnvironment(
              ZEnvironment(ApiContext(null, ApplicationContext(Some(owner))))
            )
            .either
        )
        .getOrThrowFiberFailure()
    }
    result.left.toOption.get shouldBe a[InputError]
    verify(ingredients, never()).delete(ingredient.id)
  }
}
