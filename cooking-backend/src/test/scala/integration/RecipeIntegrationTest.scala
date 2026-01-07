package integration

import domain.filters.{Filters, StringFilter}
import domain.ingredients.{Ingredient, IngredientInput, Quantity, Unit as IngUnit}
import domain.recipes.{Recipe, RecipeIngredientInput, RecipeInput, RecipeUpdateInput}
import domain.users.{User, UserInput}

class RecipeIntegrationTest extends IntegrationTestFramework {
  def recipesMatch(r1: Recipe, r2: Recipe): Unit = {
    r1.id shouldBe r2.id
    r1.name shouldBe r2.name
    r1.tags.toSet shouldBe r2.tags.toSet
    r1.ingredients.map(_.ingredient.id).toSet shouldBe r2.ingredients
      .map(_.ingredient.id)
      .toSet
    r1.ingredients.map(_.quantity) shouldBe r2.ingredients.map(_.quantity)
    r1.prepTime shouldBe r2.prepTime
    r1.cookTime shouldBe r2.cookTime
    r1.servings shouldBe r2.servings
    r1.countryOfOrigin shouldBe r2.countryOfOrigin
    r1.public shouldBe r2.public
    r1.wikiLink shouldBe r2.wikiLink
    r1.instructions shouldBe r2.instructions.replace("\\n", "\n")
    r1.createdBy.id shouldBe r2.createdBy.id
  }

  def recipeInputMatches(
      input: RecipeInput,
      created: Recipe,
      ingredientIds: Seq[java.util.UUID]
  ): Unit = {
    created.name shouldBe input.name
    created.tags.sorted shouldBe input.tags.sorted
    created.prepTime shouldBe input.prepTime
    created.cookTime shouldBe input.cookTime
    created.servings shouldBe input.servings
    created.countryOfOrigin shouldBe input.countryOfOrigin
    created.public shouldBe input.public
    created.wikiLink.map(_.toLowerCase) shouldBe input.wikiLink.map(
      _.toLowerCase
    )
    created.instructions shouldBe input.instructions.replace("\\n", "\n")
    created.ingredients.map(_.ingredient.id).toSet shouldBe ingredientIds.toSet
    created.ingredients.map(_.quantity.amount) shouldBe input.ingredients.map(
      _.quantity.amount
    )
    created.ingredients.map(_.quantity.unit.name) shouldBe input.ingredients
      .map(_.quantity.unit.name)
    created.ingredients.map(_.description) shouldBe input.ingredients.map(
      _.description
    )
  }

  var user: User = _
  var tomato: Ingredient = _
  var onion: Ingredient = _

  override def beforeEach(): Unit = {
    super.beforeEach()
    user = createTestAdminUser(standardUserInput)
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
      ingredients = Some(
        Seq(
          RecipeIngredientInput(
            onion.id,
            Quantity(IngUnit("cup", true, ""), 2),
            description = Some("about one onion")
          )
        )
      ),
      prepTime = Some(10),
      cookTime = Some(20),
      countryOfOrigin = Some("Italy"),
      public = Some(true),
      wikiLink = Some("https://en.wikipedia.org/wiki/Soup"),
      instructions = Some(quillDelta("Chop and cook"))
    )

    val updated = updateRecipe(created, update)
    updated.id shouldBe created.id
    updated.name shouldBe "Updated Recipe"
    updated.tags.toSet shouldBe Set("dinner", "quick")
    updated.ingredients.map(_.ingredient.id) shouldBe Seq(onion.id)
    updated.ingredients.head.quantity.amount shouldBe 2.0
    updated.ingredients.head.quantity.unit.name shouldBe "cup"
    updated.ingredients.head.description shouldBe Some("about one onion")
    updated.prepTime shouldBe 10
    updated.cookTime shouldBe 20
    updated.countryOfOrigin shouldBe Some("Italy")
    updated.public shouldBe true
    updated.wikiLink.map(_.toLowerCase()) shouldBe Some(
      "https://en.wikipedia.org/wiki/soup"
    )
    updated.instructions shouldBe quillDelta("Chop and cook").replace(
      "\\n",
      "\n"
    )
    updated.ingredients.head.description shouldBe Some("about one onion")

    val fetched = getRecipeById(created.id)
    recipesMatch(updated, fetched)
  }

  it should "list recipes with filters" in {
    val r1 = createTestRecipe(standardRecipeInput(Seq(tomato)))
    val r2 = createTestRecipe(
      standardRecipeInput(Seq(onion)).copy(name = "Onion Dish")
    )

    val idFilter = Filters.empty().copy(id = Some(r1.id))
    val idResults = listRecipes(idFilter)
    idResults.length shouldBe 1
    idResults.head.id shouldBe r1.id

    val nameContainsFilter = Filters
      .empty()
      .copy(name =
        Some(
          StringFilter(
            equals = None,
            anyOf = None,
            contains = Some("onion"),
            startsWith = None,
            endsWith = None
          )
        )
      )
    val nameResults = listRecipes(nameContainsFilter)
    nameResults.map(_.id).toSet shouldBe Set(r2.id)
  }

  it should "ingredient similarity filter returns two recipes ordered and filters out third by min score" in {
    val garlic = createTestIngredient(
      IngredientsIntegrationTestData.onion.copy(
        name = "Garlic",
        aliases = Seq("garlic"),
        wikiLink = "https://en.wikipedia.org/wiki/Garlic"
      )
    )
    val target = createTestRecipe(
      RecipeInput(
        name = "Target",
        tags = Seq("similarity-test"),
        ingredients = Seq(
          RecipeIngredientInput(
            tomato.id,
            Quantity(IngUnit("gram", false, ""), 100)
          ),
          RecipeIngredientInput(
            onion.id,
            Quantity(IngUnit("gram", false, ""), 100)
          )
        ),
        prepTime = 5,
        cookTime = 10,
        servings = 4,
        countryOfOrigin = Some("USA"),
        public = true,
        wikiLink = Some("https://en.wikipedia.org/wiki/Recipe"),
        instructions = quillDelta("Cook")
      )
    )

    val candidateHigh = createTestRecipe(
      RecipeInput(
        name = "High Similarity",
        tags = Seq("similarity-test"),
        ingredients = Seq(
          RecipeIngredientInput(
            tomato.id,
            Quantity(IngUnit("gram", false, ""), 100)
          ),
          RecipeIngredientInput(
            onion.id,
            Quantity(IngUnit("gram", false, ""), 100)
          )
        ),
        prepTime = 5,
        cookTime = 10,
        servings = 4,
        countryOfOrigin = Some("USA"),
        public = true,
        wikiLink = Some("https://en.wikipedia.org/wiki/Recipe"),
        instructions = quillDelta("Cook")
      )
    )

    val candidateMedium = createTestRecipe(
      RecipeInput(
        name = "Medium Similarity",
        tags = Seq("similarity-test"),
        ingredients = Seq(
          RecipeIngredientInput(
            tomato.id,
            Quantity(IngUnit("gram", false, ""), 100)
          )
        ),
        prepTime = 5,
        cookTime = 10,
        servings = 4,
        countryOfOrigin = Some("USA"),
        public = true,
        wikiLink = Some("https://en.wikipedia.org/wiki/Recipe"),
        instructions = quillDelta("Cook")
      )
    )

    val candidateLow = createTestRecipe(
      RecipeInput(
        name = "Low Similarity",
        tags = Seq("similarity-test"),
        ingredients = Seq(
          RecipeIngredientInput(
            garlic.id,
            Quantity(IngUnit("gram", false, ""), 100)
          )
        ),
        prepTime = 5,
        cookTime = 10,
        servings = 4,
        countryOfOrigin = Some("USA"),
        public = true,
        wikiLink = Some("https://en.wikipedia.org/wiki/Recipe"),
        instructions = quillDelta("Cook")
      )
    )

    val filters = Filters
      .empty()
      .copy(
        analyzedRecipe = Some(target.id),
        ingredientSimilarity = Some(
          domain.filters.SimilarityFilter(
            alpha = 0.0,
            beta = 1.0,
            gamma = 0.0,
            minScore = 0.4
          )
        )
      )

    val results = listRecipes(filters)

    results.map(_.id) shouldBe Seq(candidateHigh.id, candidateMedium.id)
    results.forall(_.id != target.id) shouldBe true
    results.forall(_.id != candidateLow.id) shouldBe true
  }

  it should "recommend recipes based on user preference" in {
    val targetUser = createTestAdminUser(UserInput("Target User", "target@example.com"))

    val beef = createTestIngredient(
      IngredientInput("Beef", Seq(), "wiki-beef", Seq("meat"))
    )
    val chicken =
      createTestIngredient(
        IngredientInput("Chicken", Seq(), "wiki-chicken", Seq("meat"))
      )
    val lettuce =
      createTestIngredient(
        IngredientInput("Lettuce", Seq(), "wiki-lettuce", Seq("veg"))
      )

    login(targetUser.id)
    createTestRecipe(
      standardRecipeInput(Seq(beef)).copy(name = "Target Beef Recipe")
    )

    val otherUser =
      createTestAdminUser(UserInput("Other User", "other@example.com"))
    login(otherUser.id)

    val candidateBeef = createTestRecipe(
      standardRecipeInput(Seq(beef)).copy(name = "Candidate Beef")
    )

    val candidateChicken = createTestRecipe(
      standardRecipeInput(Seq(chicken)).copy(name = "Candidate Chicken")
    )

    val candidateLettuce = createTestRecipe(
      standardRecipeInput(Seq(lettuce)).copy(name = "Candidate Lettuce")
    )

    val filters = Filters
      .empty()
      .copy(
        analyzedUser = Some(targetUser.id),
        ingredientSimilarity = Some(
          domain.filters.SimilarityFilter(
            alpha = 0.0,
            beta = 1.0,
            gamma = 0.0,
            minScore = 0.001
          )
        )
      )
    login(targetUser.id)

    val results = listRecipes(filters)

    results.map(_.id) should contain(candidateBeef.id)

    results.map(_.id) should contain(candidateBeef.id)
    results.map(_.id) should not contain candidateLettuce.id

    println(results.map(_.name))

    results.head.id shouldBe candidateBeef.id
  }

  it should "delete a recipe" in {
    val created = createTestRecipe(standardRecipeInput(Seq(tomato)))
    val fetched = getRecipeById(created.id)
    recipesMatch(created, fetched)

    val deleted = deleteRecipe(created.id)
    deleted.id shouldBe created.id
  }

  it should "exclude analyzed recipe from recommendations" in {
     val user = createTestAdminUser(UserInput("Self Exclusion User", "selfExcluded@example.com"))
     login(user.id)
     
     val ingredient = createTestIngredient(IngredientInput("SelfExcludedIngredient", Seq(), "link", Seq()))
     val recipe = createTestRecipe(standardRecipeInput(Seq(ingredient)).copy(name = "Analyzed Recipe"))
     val otherRecipe = createTestRecipe(standardRecipeInput(Seq(ingredient)).copy(name = "Other Recipe"))
     
     val filters = Filters.empty().copy(
       analyzedRecipe = Some(recipe.id),
       ingredientSimilarity = Some(
          domain.filters.SimilarityFilter(
            alpha = 0.0,
            beta = 1.0,
            gamma = 0.0,
            minScore = 0.0
          )
       )
     )
     
     val results = listRecipes(filters)
     println(s"Results: $results")

     results.map(_.id) should contain(otherRecipe.id)
     results.map(_.id) should not contain(recipe.id)
  }

  it should "exclude recipes owned or saved by the analyzed user from recommendations" in {
    val targetUser = createTestAdminUser(UserInput("Exclusion Target", "exclusions@example.com"))
    val otherUser = createTestAdminUser(UserInput("Other Author", "otherAuthor@example.com"))
    
    val commonIngredient = createTestIngredient(IngredientInput("CommonIng", Seq(), "link", Seq()))

    login(targetUser.id)
    val ownedRecipe = createTestRecipe(standardRecipeInput(Seq(commonIngredient)).copy(name = "Owned Recipe"))
    
    login(otherUser.id)
    val savedRecipe = createTestRecipe(standardRecipeInput(Seq(commonIngredient)).copy(name = "Saved Recipe"))
    val recommendedRecipe = createTestRecipe(standardRecipeInput(Seq(commonIngredient)).copy(name = "Recommended Recipe"))
    
    login(targetUser.id)
    saveRecipe(savedRecipe.id)
    
    val filters = Filters.empty().copy(
       analyzedUser = Some(targetUser.id),
       ingredientSimilarity = Some(
          domain.filters.SimilarityFilter(
            alpha = 0.0,
            beta = 1.0,
            gamma = 0.0,
            minScore = 0.0
          )
       )
    )
    
    val results = listRecipes(filters)
    
    results.map(_.id) should contain(recommendedRecipe.id)
    results.map(_.id) should not contain(ownedRecipe.id)
    results.map(_.id) should not contain(savedRecipe.id)
  }


  object IngredientsIntegrationTestData {
    val tomato: IngredientInput = IngredientInput(
      name = "Tomato",
      aliases = Seq("tomato", "tomatoes"),
      wikiLink = "https://en.wikipedia.org/wiki/Tomato",
      tags = Seq("vegetable", "fruit")
    )

    val onion: IngredientInput = IngredientInput(
      name = "Onion",
      aliases = Seq("onion", "onions"),
      wikiLink = "https://en.wikipedia.org/wiki/Onion",
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
      servings = 4,
      countryOfOrigin = Some("USA"),
      public = true,
      wikiLink = Some("https://en.wikipedia.org/wiki/Recipe"),
      instructions = quillDelta("Mix and cook")
    )
  }
}
