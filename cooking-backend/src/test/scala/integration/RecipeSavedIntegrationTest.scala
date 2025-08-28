package integration

import domain.filters.Filters
import domain.ingredients.{Ingredient, IngredientInput, Quantity, Unit => IngUnit}
import domain.recipes.{Recipe, RecipeIngredientInput, RecipeInput}
import domain.users.{User, UserInput}

class RecipeSavedIntegrationTest extends IntegrationTestFramework {
  var creator: User = _
  var saver: User = _
  var tomato: Ingredient = _
  var onion: Ingredient = _

  override def beforeEach(): Unit = {
    super.beforeEach()
    creator = createTestUser(standardUserInput.copy(email = "creator@example.com"))
    login(creator.id)
    tomato = createTestIngredient(IngredientInput(
      name = "Tomato",
      aliases = Seq("tomato", "tomatoes"),
      wikiLink = "https://en.wikipedia.org/wiki/Tomato",
      vegetarian = true,
      vegan = true,
      tags = Seq("vegetable", "fruit")
    ))
    onion = createTestIngredient(IngredientInput(
      name = "Onion",
      aliases = Seq("onion", "onions"),
      wikiLink = "https://en.wikipedia.org/wiki/Onion",
      vegetarian = true,
      vegan = true,
      tags = Seq("vegetable")
    ))
  }

  private def buildRecipeInput(ingredients: Seq[Ingredient], public: Boolean = true, name: String = "Saved Test Recipe"): RecipeInput = {
    val ingredientInputs = ingredients.map(i => RecipeIngredientInput(i.id, Quantity(IngUnit("Piece", false, ""), 1)))
    RecipeInput(
      name = name,
      tags = Seq("quick", "easy"),
      ingredients = ingredientInputs,
      prepTime = 5,
      cookTime = 10,
      vegetarian = true,
      vegan = true,
      countryOfOrigin = Some("USA"),
      public = public,
      wikiLink = Some("https://en.wikipedia.org/wiki/Recipe"),
      instructions = "Mix and cook"
    )
  }

  it should "save a recipe and fetch it using savedByUser filter" in {
    val recipe = createTestRecipe(buildRecipeInput(Seq(tomato, onion), public = true))

    saver = createTestUser(UserInput(name = "Saver", email = "saver@example.com", password = "pw"))
    login(saver.id)

    val saved = saveRecipe(recipe.id)
    saved.id shouldBe recipe.id

    val savedForSaver = listSavedRecipes(saver.id)
    savedForSaver.map(_.id).toSet shouldBe Set(recipe.id)

    login(creator.id)
    val savedForCreator = listSavedRecipes(creator.id)
    savedForCreator.map(_.id) shouldBe Seq.empty
  }

  it should "not allow saving private recipes and should return an error" in {
    val privateRecipe = createTestRecipe(buildRecipeInput(Seq(tomato), public = false, name = "Private Recipe"))

    saver = createTestUser(UserInput(name = "Saver2", email = "saver2@example.com", password = "pw"))
    login(saver.id)

    val failed = scala.util.Try(saveRecipe(privateRecipe.id))
    failed.isFailure shouldBe true
  }
}
