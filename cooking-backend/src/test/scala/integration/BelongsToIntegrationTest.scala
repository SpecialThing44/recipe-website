package integration

import domain.filters.Filters
import domain.ingredients.{Ingredient, IngredientInput, Quantity, Unit as IngUnit}
import domain.recipes.{RecipeIngredientInput, RecipeInput}
import domain.users.User

class BelongsToIntegrationTest extends IntegrationTestFramework {
  var user1: User = _
  var user2: User = _
  var tomato: Ingredient = _

  override def beforeEach(): Unit = {
    super.beforeEach()
    user1 = createTestAdminUser(standardUserInput)
    login(user1.id)
    tomato = createTestIngredient(
      IngredientInput(
        name = "Tomato",
        aliases = Seq("tomato"),
        wikiLink = "https://en.wikipedia.org/wiki/Tomato",
        tags = Seq("vegetable")
      )
    )
  }

  def recipeInputWith(
      ingredients: Seq[Ingredient],
      public: Boolean,
      name: String
  ): RecipeInput = {
    val ingredientInputs = ingredients.map(i =>
      RecipeIngredientInput(i.id, Quantity(IngUnit("piece", false, ""), 1))
    )
    RecipeInput(
      name = name,
      tags = Seq("t"),
      ingredients = ingredientInputs,
      prepTime = 1,
      cookTime = 1,
      countryOfOrigin = None,
      public = public,
      wikiLink = None,
      instructions = quillDelta("inst")
    )
  }

  it should "list ingredients that belong to a user when filtering by belongsToUser" in {
    val i1 = tomato
    val i2 = createTestIngredient(
      IngredientInput(
        name = "Onion",
        aliases = Seq("onion"),
        wikiLink = "https://en.wikipedia.org/wiki/Onion",
        tags = Seq("vegetable")
      )
    )

    val resultsForA =
      listIngredients(Filters.empty().copy(belongsToUser = Some(user1.id)))
    resultsForA.map(_.id).toSet should contain allOf (i1.id, i2.id)

    logout()
    user2 = createTestAdminUser(
      standardUserInput.copy(email = "other@example.com", name = "Other")
    )
    login(user2.id)
    val i3 = createTestIngredient(
      IngredientInput(
        name = "Chicken",
        aliases = Seq("chicken"),
        wikiLink = "https://en.wikipedia.org/wiki/Chicken",
        tags = Seq("meat")
      )
    )

    val resultsForB =
      listIngredients(Filters.empty().copy(belongsToUser = Some(user2.id)))
    resultsForB.map(_.id).toSet shouldBe Set(i3.id)
  }

  it should "list only own or public recipes when filtering by belongsToUser and exclude private recipes of others" in {
    val recipePublic1 = createTestRecipe(
      recipeInputWith(Seq(tomato), public = true, name = "A Public")
    )
    val recipePrivate1 = createTestRecipe(
      recipeInputWith(Seq(tomato), public = false, name = "A Private")
    )

    logout()
    user2 = createTestAdminUser(
      standardUserInput.copy(email = "b@example.com", name = "B User")
    )
    login(user2.id)
    val recipePublic2 = createTestRecipe(
      recipeInputWith(Seq(tomato), public = true, name = "B Public")
    )
    val recipePrivate2 = createTestRecipe(
      recipeInputWith(Seq(tomato), public = false, name = "B Private")
    )
    login(user1.id)
    val user1Recipes =
      listRecipes(Filters.empty().copy(belongsToUser = Some(user1.id)))
    user1Recipes.map(_.id).toSet shouldBe Set(
      recipePublic1.id,
      recipePrivate1.id
    )
    login(user2.id)
    val user2Recipes =
      listRecipes(Filters.empty().copy(belongsToUser = Some(user2.id)))
    user2Recipes.map(_.id).toSet shouldBe Set(
      recipePublic2.id,
      recipePrivate2.id
    )

    logout()
    login(user1.id)
    val visibleTo1 = listRecipes(Filters.empty())
    val visibleTo1Names = visibleTo1.map(_.name).toSet
    visibleTo1Names should not contain ("B Private")
    visibleTo1Names should contain("B Public")
  }
}
