package api.recipes

import domain.food.recipes.{Recipe, RecipeInput}

import java.time.Instant

object RecipeAdapter {
  def adapt(recipe: RecipeInput): Recipe = {
    val now = Instant.now
    Recipe(
      id = None, // Assuming a new UUID is generated for each new recipe
      name = recipe.name,
      user = recipe.user,
      aliases = recipe.aliases,
      tags = recipe.tags,
      ingredients = recipe.ingredients,
      prepTime = recipe.prepTime,
      cookTime = recipe.cookTime,
      vegetarian = recipe.vegetarian,
      vegan = recipe.vegan,
      countryOfOrigin = recipe.countryOfOrigin,
      cuisine = recipe.cuisine,
      public = recipe.public,
      wikiLink = recipe.wikiLink,
      videoLink = recipe.videoLink,
      instructions = recipe.instructions,
      createdOn = now,
      updatedOn = now
    )
  }
}
