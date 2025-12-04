package integration

import domain.filters.{Filters, SimilarityFilter}
import domain.ingredients.{Ingredient, IngredientInput, Quantity, Unit => IngUnit}
import domain.recipes.{RecipeInput, RecipeIngredientInput}
import domain.users.UserInput

class UsersAnalyticalIntegrationTest extends IntegrationTestFramework {
  private def ingredientInputTomato: IngredientInput = IngredientInput(
    name = "Tomato",
    aliases = Seq("tomato", "tomatoes"),
    wikiLink = "https://en.wikipedia.org/wiki/Tomato",
    vegetarian = true,
    vegan = true,
    tags = Seq("vegetable", "fruit")
  )

  private def ingredientInputOnion: IngredientInput = IngredientInput(
    name = "Onion",
    aliases = Seq("onion", "onions"),
    wikiLink = "https://en.wikipedia.org/wiki/Onion",
    vegetarian = true,
    vegan = true,
    tags = Seq("vegetable")
  )

  private def ingredientInputGarlic: IngredientInput = IngredientInput(
    name = "Garlic",
    aliases = Seq("garlic"),
    wikiLink = "https://en.wikipedia.org/wiki/Garlic",
    vegetarian = true,
    vegan = true,
    tags = Seq("vegetable")
  )

  private def grams(value: Int): Quantity = Quantity(IngUnit("gram", false, ""), value)

  private def recipeInput(name: String, tags: Seq[String], ingredients: Seq[Ingredient]): RecipeInput =
    RecipeInput(
      name = name,
      tags = tags,
      ingredients = ingredients.map(i => RecipeIngredientInput(i.id, grams(100))),
      prepTime = 5,
      cookTime = 10,
      vegetarian = true,
      vegan = true,
      countryOfOrigin = Some("USA"),
      public = true,
      wikiLink = Some("https://en.wikipedia.org/wiki/Recipe"),
      instructions = quillDelta("Cook")
    )

  it should "rank users by ingredient similarity and apply minScore, excluding the analyzed user" in {
    val target = createTestAdminUser(UserInput("Target User", "target@example.com", "password"))
    val candidateA = createTestAdminUser(UserInput("Candidate A", "candA@example.com", "password"))
    val candidateB = createTestAdminUser(UserInput("Candidate B", "candB@example.com", "password"))
    val candidateD = createTestAdminUser(UserInput("Candidate D", "candD@example.com", "password"))
    login(target.id)
    val tomato = createTestIngredient(ingredientInputTomato)
    val onion = createTestIngredient(ingredientInputOnion)
    val garlic = createTestIngredient(ingredientInputGarlic)


    createTestRecipe(recipeInput("T-Recipe", Seq("common", "ta"), Seq(tomato, onion)))

    login(candidateA.id)
    createTestRecipe(recipeInput("A-Recipe", Seq("common", "ta"), Seq(tomato, onion)))

    login(candidateB.id)
    createTestRecipe(recipeInput("B-Recipe", Seq("common"), Seq(tomato)))

    login(candidateD.id)
    createTestRecipe(recipeInput("D-Recipe", Seq("other"), Seq(garlic)))

    login(target.id)
    val filters = Filters.empty().copy(
      analyzedEntity = Some(target.id),
      ingredientSimilarity = Some(SimilarityFilter(alpha = 0.0, beta = 1.0, gamma = 0.0, minScore = 0.4))
    )

    val results = listUsers(filters)

    results.map(_.id) shouldBe Seq(candidateA.id, candidateB.id)
    results.exists(_.id == target.id) shouldBe false
    results.exists(_.id == candidateD.id) shouldBe false
  }

  it should "rank users by tag similarity" in {
    val target = createTestAdminUser(UserInput("Tag Target", "tagtarget@example.com", "password"))
    val candidateA = createTestAdminUser(UserInput("Tag Candidate A", "tagcanda@example.com", "password"))
    val candidateB = createTestAdminUser(UserInput("Tag Candidate B", "tagcandb@example.com", "password"))
    login(target.id)
    val tomato = createTestIngredient(ingredientInputTomato)
    val onion = createTestIngredient(ingredientInputOnion)

    createTestRecipe(recipeInput("TT-1", Seq("common", "ta"), Seq(tomato, onion)))

    login(candidateA.id)
    createTestRecipe(recipeInput("TA-1", Seq("common", "ta"), Seq(tomato, onion)))

    login(candidateB.id)
    createTestRecipe(recipeInput("TB-1", Seq("common"), Seq(tomato)))

    login(target.id)
    val filters = Filters.empty().copy(
      analyzedEntity = Some(target.id),
      tagSimilarity = Some(SimilarityFilter(alpha = 0.0, beta = 0.0, gamma = 1.0, minScore = 0.3))
    )

    val results = listUsers(filters)

    results.map(_.id) shouldBe Seq(candidateA.id, candidateB.id)
  }

  it should "rank users by co-save similarity and respect minScore" in {
    val target = createTestAdminUser(UserInput("Save Target", "savetarget@example.com", "password"))
    val candidateA = createTestAdminUser(UserInput("Save Candidate A", "savecanda@example.com", "password"))
    val candidateB = createTestAdminUser(UserInput("Save Candidate B", "savecandb@example.com", "password"))
    login(target.id)

    val tomato = createTestIngredient(ingredientInputTomato)

    login(candidateA.id)
    val r1 = createTestRecipe(recipeInput("S-1", Seq("savetest"), Seq(tomato)))
    val r2 = createTestRecipe(recipeInput("S-2", Seq("savetest"), Seq(tomato)))

    login(target.id)
    saveRecipe(r1.id)
    saveRecipe(r2.id)

    login(candidateB.id)
    saveRecipe(r1.id)

    login(target.id)
    val filtersStrict = Filters.empty().copy(
      analyzedEntity = Some(target.id),
      coSaveSimilarity = Some(SimilarityFilter(alpha = 1.0, beta = 0.0, gamma = 0.0, minScore = 0.6))
    )

    val strictResults = listUsers(filtersStrict)
    strictResults.map(_.id) shouldBe Seq(candidateA.id)

    val filtersLoose = filtersStrict.copy(coSaveSimilarity = Some(SimilarityFilter(alpha = 1.0, beta = 0.0, gamma = 0.0, minScore = 0.4)))
    val looseResults = listUsers(filtersLoose)
    looseResults.map(_.id) shouldBe Seq(candidateA.id, candidateB.id)
  }

  it should "rank users by combined ingredient, tag, and co-save similarity" in {
    val target = createTestAdminUser(UserInput("Combo Target", "combotarget@example.com", "password"))
    val candidateA = createTestAdminUser(UserInput("Combo Candidate A", "combocanda@example.com", "password"))
    val candidateB = createTestAdminUser(UserInput("Combo Candidate B", "combocandb@example.com", "password"))
    login(target.id)
    val tomato = createTestIngredient(ingredientInputTomato)
    val onion = createTestIngredient(ingredientInputOnion)

    login(candidateA.id)
    val tRecipe = createTestRecipe(recipeInput("C-T", Seq("common", "ta"), Seq(tomato, onion)))
    val aRecipe = createTestRecipe(recipeInput("C-A", Seq("common", "ta"), Seq(tomato, onion)))

    login(candidateB.id)
    val bRecipe = createTestRecipe(recipeInput("C-B", Seq("common"), Seq(tomato)))
    saveRecipe(tRecipe.id)

    login(target.id)
    saveRecipe(tRecipe.id)
    saveRecipe(aRecipe.id)

    val filters = Filters.empty().copy(
      analyzedEntity = Some(target.id),
      ingredientSimilarity = Some(SimilarityFilter(alpha = 0.0, beta = 1.0, gamma = 0.0, minScore = 0.0)),
      coSaveSimilarity = Some(SimilarityFilter(alpha = 1.0, beta = 0.0, gamma = 0.0, minScore = 0.0)),
      tagSimilarity = Some(SimilarityFilter(alpha = 0.0, beta = 0.0, gamma = 1.0, minScore = 0.0))
    )

    val results = listUsers(filters)
    results.map(_.id) shouldBe Seq(candidateA.id, candidateB.id)
  }
}
