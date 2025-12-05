package api.recipes

import domain.recipes.{Recipe, RecipeInput, RecipeUpdateInput}

import java.time.Instant
import java.util.UUID

object RecipeAdapter {
  def adapt(
      recipe: RecipeInput,
      resolvedIngredients: Seq[domain.ingredients.InstructionIngredient],
      createdBy: domain.users.User
  ): Recipe = {
    val now = Instant.now
    Recipe(
      name = recipe.name,
      createdBy = createdBy,
      tags = recipe.tags,
      ingredients = resolvedIngredients,
      prepTime = recipe.prepTime,
      cookTime = recipe.cookTime,
      vegetarian = recipe.vegetarian,
      vegan = recipe.vegan,
      countryOfOrigin = recipe.countryOfOrigin,
      public = recipe.public,
      wikiLink = recipe.wikiLink,
      instructions = recipe.instructions,
      instructionImages = Seq.empty,
      image = None,
      createdOn = now,
      updatedOn = now,
      id = UUID.randomUUID()
    )
  }

  def adaptUpdate(
      input: RecipeUpdateInput,
      original: Recipe,
      resolvedIngredients: Option[Seq[domain.ingredients.InstructionIngredient]]
  ): Recipe = {
    val now = Instant.now
    Recipe(
      name = input.name.getOrElse(original.name),
      createdBy = original.createdBy,
      tags = input.tags.getOrElse(original.tags),
      ingredients = resolvedIngredients.getOrElse(original.ingredients),
      prepTime = input.prepTime.getOrElse(original.prepTime),
      cookTime = input.cookTime.getOrElse(original.cookTime),
      vegetarian = input.vegetarian.getOrElse(original.vegetarian),
      vegan = input.vegan.getOrElse(original.vegan),
      countryOfOrigin =
        if (input.countryOfOrigin.isDefined) input.countryOfOrigin
        else original.countryOfOrigin,
      public = input.public.getOrElse(original.public),
      wikiLink = input.wikiLink.orElse(original.wikiLink),
      instructions = input.instructions.getOrElse(original.instructions),
      instructionImages =
        input.instructionImages.getOrElse(original.instructionImages),
      image = if (input.image.isDefined) input.image else original.image,
      createdOn = original.createdOn,
      updatedOn = now,
      id = original.id
    )
  }
}
