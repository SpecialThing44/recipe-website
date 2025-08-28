package integration

import domain.filters.{Filters, StringFilter}
import domain.ingredients.{Ingredient, IngredientInput, Quantity, Unit => IngUnit}
import domain.recipes.{Recipe, RecipeInput, RecipeIngredientInput, RecipeUpdateInput}
import domain.users.User

class RecipeIntegrationTest extends IntegrationTestFramework {
  def recipesMatch(r1: Recipe, r2: Recipe): Unit = {
    r1.id shouldBe r2.id
    r1.name shouldBe r2.name
    r1.tags.toSet shouldBe r2.tags.toSet
    r1.ingredients.map(_.ingredient.id).toSet shouldBe r2.ingredients.map(_.ingredient.id).toSet
    r1.ingredients.map(_.quantity) shouldBe r2.ingredients.map(_.quantity)
    r1.prepTime shouldBe r2.prepTime
    r1.cookTime shouldBe r2.cookTime
    r1.vegetarian shouldBe r2.vegetarian
    r1.vegan shouldBe r2.vegan
    r1.countryOfOrigin shouldBe r2.countryOfOrigin
    r1.public shouldBe r2.public
    r1.wikiLink shouldBe r2.wikiLink
    r1.instructions shouldBe r2.instructions
    r1.createdBy.id shouldBe r2.createdBy.id
  }

  def recipeInputMatches(input: RecipeInput, created: Recipe, ingredientIds: Seq[java.util.UUID]): Unit = {
    created.name shouldBe input.name
    created.tags shouldBe input.tags
    created.prepTime shouldBe input.prepTime
    created.cookTime shouldBe input.cookTime
    created.vegetarian shouldBe input.vegetarian
    created.vegan shouldBe input.vegan
    created.countryOfOrigin shouldBe input.countryOfOrigin
    created.public shouldBe input.public
    created.wikiLink.map(_.toLowerCase) shouldBe input.wikiLink.map(_.toLowerCase)
    created.instructions shouldBe input.instructions
    created.ingredients.map(_.ingredient.id).toSet shouldBe ingredientIds.toSet
    created.ingredients.map(_.quantity.amount) shouldBe input.ingredients.map(_.quantity.amount)
    created.ingredients.map(_.quantity.unit.name) shouldBe input.ingredients.map(_.quantity.unit.name)
  }

  var user: User = _
  var tomato: Ingredient = _
  var onion: Ingredient = _

  override def beforeEach(): Unit = {
    super.beforeEach()
    user = createTestUser(standardUserInput)
    login(user.id)
    tomato = createTestIngredient(IngredientsIntegrationTestData.tomato)
    onion = createTestIngredient(IngredientsIntegrationTestData.onion)
  }

  it should "create a recipe and fetch it by ID" in {
    val input = standardRecipeInput(Seq(tomato, onion))
    val created = createTestRecipe(input)
    recipeInputMatches(input, created, Seq(tomato.id, onion.id))
    created.createdBy.id shouldBe user.id

    val fetched = getRecipeById(created.id)
    recipesMatch(created, fetched)
  }

  it should "update a recipe fields and ingredients" in {
    val input = standardRecipeInput(Seq(tomato))
    val created = createTestRecipe(input)

    val update = RecipeUpdateInput(
      name = Some("Updated Recipe"),
      tags = Some(Seq("dinner", "quick")),
      ingredients = Some(Seq(RecipeIngredientInput(onion.id, Quantity(IngUnit("cup", true, ""), 2)))) ,
      prepTime = Some(10),
      cookTime = Some(20),
      vegetarian = Some(true),
      vegan = Some(true),
      countryOfOrigin = Some("Italy"),
      public = Some(true),
      wikiLink = Some("https://en.wikipedia.org/wiki/Soup"),
      instructions = Some("Chop and cook")
    )

    val updated = updateRecipe(created, update)
    updated.id shouldBe created.id
    updated.name shouldBe "Updated Recipe"
    updated.tags.toSet shouldBe Set("dinner", "quick")
    updated.ingredients.map(_.ingredient.id) shouldBe Seq(onion.id)
    updated.ingredients.head.quantity.amount shouldBe 2
    updated.ingredients.head.quantity.unit.name shouldBe "cup"
    updated.prepTime shouldBe 10
    updated.cookTime shouldBe 20
    updated.vegetarian shouldBe true
    updated.vegan shouldBe true
    updated.countryOfOrigin shouldBe Some("Italy")
    updated.public shouldBe true
    updated.wikiLink.map(_.toLowerCase()) shouldBe Some("https://en.wikipedia.org/wiki/soup")
    updated.instructions shouldBe "Chop and cook"

    val fetched = getRecipeById(created.id)
    recipesMatch(updated, fetched)
  }

  it should "list recipes with filters" in {
    val r1 = createTestRecipe(standardRecipeInput(Seq(tomato)))
    val r2 = createTestRecipe(standardRecipeInput(Seq(onion)).copy(name = "Onion Dish"))

    val idFilter = Filters.empty().copy(id = Some(r1.id))
    val idResults = listRecipes(idFilter)
    idResults.length shouldBe 1
    idResults.head.id shouldBe r1.id

    val nameContainsFilter = Filters.empty().copy(name = Some(StringFilter(equals = None, anyOf = None, contains = Some("onion"), startsWith = None, endsWith = None)))
    val nameResults = listRecipes(nameContainsFilter)
    nameResults.map(_.id).toSet shouldBe Set(r2.id)

    val vegetarianFilter = Filters.empty().copy(vegetarian = Some(true))
    val vegetarianResults = listRecipes(vegetarianFilter)
    vegetarianResults.forall(_.vegetarian) shouldBe true
  }

  it should "delete a recipe" in {
    val created = createTestRecipe(standardRecipeInput(Seq(tomato)))
    val fetched = getRecipeById(created.id)
    recipesMatch(created, fetched)

    val deleted = deleteRecipe(created.id)
    deleted.id shouldBe created.id
  }

  object IngredientsIntegrationTestData {
    val tomato: IngredientInput = IngredientInput(
      name = "Tomato",
      aliases = Seq("tomato", "tomatoes"),
      wikiLink = "https://en.wikipedia.org/wiki/Tomato",
      vegetarian = true,
      vegan = true,
      tags = Seq("vegetable", "fruit")
    )

    val onion: IngredientInput = IngredientInput(
      name = "Onion",
      aliases = Seq("onion", "onions"),
      wikiLink = "https://en.wikipedia.org/wiki/Onion",
      vegetarian = true,
      vegan = true,
      tags = Seq("vegetable")
    )
  }

  def standardRecipeInput(ingredients: Seq[Ingredient]): RecipeInput = {
    val ingredientInputs = ingredients.map { ing =>
      RecipeIngredientInput(ing.id, Quantity(IngUnit("piece", false, ""), 1))
    }
    RecipeInput(
      name = "Test Recipe",
      tags = Seq("quick", "easy"),
      ingredients = ingredientInputs,
      prepTime = 5,
      cookTime = 10,
      vegetarian = true,
      vegan = true,
      countryOfOrigin = Some("USA"),
      public = true,
      wikiLink = Some("https://en.wikipedia.org/wiki/Recipe"),
      instructions = "Mix and cook"
    )
  }
}
