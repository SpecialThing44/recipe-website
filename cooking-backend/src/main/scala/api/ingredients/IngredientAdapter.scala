package api.ingredients

import domain.ingredients.{Ingredient, IngredientInput, IngredientUpdateInput}
import domain.users.User

import java.util.UUID

object IngredientAdapter {
  def adapt(input: IngredientInput, createdBy: User): Ingredient = {
    Ingredient(
      name = input.name,
      aliases = input.aliases,
      wikiLink = input.wikiLink,
      vegetarian = input.vegetarian,
      vegan = input.vegan,
      tags = input.tags,
      createdBy = createdBy,
      id = UUID.randomUUID()
    )
  }

  def adaptUpdate(
      input: IngredientUpdateInput,
      original: Ingredient
  ): Ingredient = {
    Ingredient(
      name = input.name.getOrElse(original.name),
      aliases = input.aliases.getOrElse(original.aliases),
      wikiLink = input.wikiLink.getOrElse(original.wikiLink),
      vegetarian = input.vegetarian.getOrElse(original.vegetarian),
      vegan = input.vegan.getOrElse(original.vegan),
      tags = input.tags.getOrElse(original.tags),
      createdBy = original.createdBy,
      id = original.id
    )
  }
}
