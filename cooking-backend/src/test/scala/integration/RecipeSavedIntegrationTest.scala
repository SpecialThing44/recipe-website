package integration

import domain.filters.Filters
import domain.ingredients.{Ingredient, IngredientInput, Quantity, Unit as IngUnit}
import domain.recipes.{Recipe, RecipeIngredientInput, RecipeInput}
import domain.users.{User, UserInput}

class RecipeSavedIntegrationTest extends IntegrationTestFramework {
  var creator: User = _
  var saver: User = _
  var tomato: Ingredient = _
  var onion: Ingredient = _

  override def beforeEach(): Unit = {
    super.beforeEach()
    creator = createTestAdminUser(
      standardUserInput.copy(email = "creator@example.com")
    )
    login(creator.id)
    tomato = createTestIngredient(
      IngredientInput(
        name = "Tomato",
        aliases = Seq("tomato", "tomatoes"),
        wikiLink = "https://en.wikipedia.org/wiki/Tomato",
        tags = Seq("vegetable", "fruit")
      )
    )
    onion = createTestIngredient(
      IngredientInput(
        name = "Onion",
        aliases = Seq("onion", "onions"),
        wikiLink = "https://en.wikipedia.org/wiki/Onion",
        tags = Seq("vegetable")
      )
    )
  }

  private def buildRecipeInput(
      ingredients: Seq[Ingredient],
      public: Boolean = true,
      name: String = "Saved Test Recipe"
  ): RecipeInput = {
    val ingredientInputs = ingredients.map(i =>
      RecipeIngredientInput(i.id, Quantity(IngUnit("Piece", false, ""), 1))
    )
    RecipeInput(
      name = name,
      tags = Seq("quick", "easy"),
      ingredients = ingredientInputs,
      prepTime = 5,
      cookTime = 10,
      servings = 4,
      countryOfOrigin = Some("USA"),
      public = public,
      wikiLink = Some("https://en.wikipedia.org/wiki/Recipe"),
      instructions = quillDelta("Mix and cook")
    )
  }

  it should "save a recipe and fetch it using savedByUser filter" in {
    val recipe =
      createTestRecipe(buildRecipeInput(Seq(tomato, onion), public = true))

    saver =
      createTestUser(UserInput(name = "Saver", email = "saver@example.com"))
    login(saver.id)

    val saved = saveRecipe(recipe.id)
    saved.id shouldBe recipe.id

    val savedForSaver = listSavedRecipes(saver.id)
    savedForSaver.map(_.id).toSet shouldBe Set(recipe.id)

    login(creator.id)
    val savedForCreator = listSavedRecipes(creator.id)
    savedForCreator.map(_.id) shouldBe Seq.empty
  }

  it should "allow saving recipes even when input public flag is false" in {
    val privateRecipe = createTestRecipe(
      buildRecipeInput(Seq(tomato), public = false, name = "Private Recipe")
    )

    saver =
      createTestUser(UserInput(name = "Saver2", email = "saver2@example.com"))
    login(saver.id)

    val saved = saveRecipe(privateRecipe.id)
    saved.id shouldBe privateRecipe.id
  }

  it should "combine saved recipes with ids, names, tags, and pagination" in {
    val first = createTestRecipe(buildRecipeInput(Seq(tomato), name = "A Soup"))
    val second = createTestRecipe(buildRecipeInput(Seq(onion), name = "B Soup"))
    createTestRecipe(buildRecipeInput(Seq(tomato, onion), name = "Unsaved Soup"))
    saver = createTestUser(UserInput(name = "Saver", email = "combined@example.com"))
    login(saver.id)
    saveRecipe(first.id)
    saveRecipe(second.id)

    val filters = Filters.empty().copy(
      savedByUser = Some(saver.id),
      ids = Some(List(first.id, second.id)),
      name = Some(domain.filters.StringFilter.empty().copy(contains = Some("Soup"))),
      tags = Some(Seq("quick")),
      orderBy = Some(domain.filters.OrderBy(Some(true))),
      limit = Some(1)
    )

    listRecipes(filters).map(_.id) shouldBe Seq(first.id)
    listRecipes(filters.copy(page = Some(1))).map(_.id) shouldBe Seq(second.id)
    listRecipes(filters.copy(page = Some(2))) shouldBe empty
  }
  it should "unsave only the current user's relationship and allow repeated removal" in {
    val recipe = createTestRecipe(buildRecipeInput(Seq(tomato)))
    val firstSaver = createTestUser(UserInput("First saver", "first-saver@example.com"))
    val secondSaver = createTestUser(UserInput("Second saver", "second-saver@example.com"))
    login(firstSaver.id)
    saveRecipe(recipe.id)
    login(secondSaver.id)
    saveRecipe(recipe.id)
    login(firstSaver.id)
    zio.Unsafe.unsafe { implicit unsafe =>
      zio.Runtime.default.unsafe.run(
        (recipeFacade.unsave(recipe.id) *> recipeFacade.unsave(recipe.id)).provideLayer(createApiContext())
      ).getOrThrowFiberFailure()
    }
    listSavedRecipes(firstSaver.id) shouldBe empty
    listSavedRecipes(secondSaver.id).map(_.id) shouldBe Seq(recipe.id)
  }

}
