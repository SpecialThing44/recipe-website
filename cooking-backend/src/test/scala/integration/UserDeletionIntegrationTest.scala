package integration

import domain.ingredients.{Ingredient, IngredientInput, Quantity, Unit as IngredientUnit}
import domain.recipes.{RecipeComponentInput, RecipeIngredientInput, RecipeInput}
import domain.types.NoSuchEntityError
import domain.users.UserInput

class UserDeletionIntegrationTest extends IntegrationTestFramework {
  private def createIngredient(): Ingredient =
    createTestIngredient(IngredientInput(
      name = "Deletion Test Ingredient",
      aliases = Seq.empty,
      wikiLink = "https://en.wikipedia.org/wiki/Tomato",
      tags = Seq.empty
    ))

  private def recipeInput(name: String, ingredient: Ingredient): RecipeInput =
    RecipeInput(
      name = name,
      tags = Seq.empty,
      ingredients = Seq(RecipeIngredientInput(
        ingredient.id,
        Quantity(IngredientUnit("gram", false, ""), 100)
      )),
      prepTime = 1,
      cookTime = 1,
      countryOfOrigin = None,
      wikiLink = None,
      instructions = quillDelta("Cook")
    )

  it should "preserve another author's recipe when its saver deletes their account" in {
    val author = createTestAdminUser(UserInput("Author", "author@example.com"))
    login(author.id)
    val ingredient = createIngredient()
    val recipe = createTestRecipe(recipeInput("Another Author's Recipe", ingredient))
    val saver = createTestUser(UserInput("Saver", "saver@example.com"))
    login(saver.id)
    saveRecipe(recipe.id)

    deleteUser(saver.id)

    getRecipeById(recipe.id).id shouldBe recipe.id
    getIngredientById(ingredient.id).id shouldBe ingredient.id
  }

  it should "preserve saved recipes and their ingredients when their author deletes their account" in {
    val author = createTestAdminUser(UserInput("Author", "author@example.com"))
    login(author.id)
    val ingredient = createIngredient()
    val recipe = createTestRecipe(recipeInput("Saved Recipe", ingredient))
    val saver = createTestUser(UserInput("Saver", "saver@example.com"))
    login(saver.id)
    saveRecipe(recipe.id)
    login(author.id)

    deleteUser(author.id)

    getRecipeById(recipe.id).ingredients.map(_.ingredient.id) shouldBe Seq(ingredient.id)
    getIngredientById(ingredient.id).id shouldBe ingredient.id
  }

  it should "preserve ingredients used by another author's recipe" in {
    val ingredientAuthor = createTestAdminUser(UserInput("Ingredient Author", "ingredient@example.com"))
    login(ingredientAuthor.id)
    val ingredient = createIngredient()
    val recipeAuthor = createTestAdminUser(UserInput("Recipe Author", "recipe@example.com"))
    login(recipeAuthor.id)
    val recipe = createTestRecipe(recipeInput("Shared Ingredient Recipe", ingredient))
    login(ingredientAuthor.id)

    deleteUser(ingredientAuthor.id)

    getRecipeById(recipe.id).ingredients.map(_.ingredient.id) shouldBe Seq(ingredient.id)
    getIngredientById(ingredient.id).id shouldBe ingredient.id
  }

  it should "preserve component recipes referenced by another author's recipe" in {
    val componentAuthor = createTestAdminUser(UserInput("Component Author", "component@example.com"))
    login(componentAuthor.id)
    val ingredient = createIngredient()
    val component = createTestRecipe(recipeInput("Component Recipe", ingredient))
    val parentAuthor = createTestAdminUser(UserInput("Parent Author", "parent@example.com"))
    login(parentAuthor.id)
    val parent = createTestRecipe(recipeInput("Parent Recipe", ingredient).copy(
      ingredients = Seq.empty,
      recipeIngredients = Seq(RecipeComponentInput(
        component.id,
        Quantity(IngredientUnit("serving", false, ""), 1)
      ))
    ))
    login(componentAuthor.id)

    deleteUser(componentAuthor.id)

    getRecipeById(parent.id).recipeIngredients.map(_.recipe.id) shouldBe Seq(component.id)
    getRecipeById(component.id).ingredients.map(_.ingredient.id) shouldBe Seq(ingredient.id)
  }

  it should "delete unused recipes and ingredients belonging to the deleted user" in {
    val author = createTestAdminUser(UserInput("Author", "author@example.com"))
    login(author.id)
    val ingredient = createIngredient()
    val recipe = createTestRecipe(recipeInput("Unused Recipe", ingredient))

    deleteUser(author.id)

    intercept[NoSuchEntityError](getRecipeById(recipe.id))
    intercept[NoSuchEntityError](getIngredientById(ingredient.id))
  }
}
