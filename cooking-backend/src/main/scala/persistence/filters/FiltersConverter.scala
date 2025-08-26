package persistence.filters

import domain.filters.Filters
import io.circe.syntax.EncoderOps
import persistence.users.UserConverter.lowerPrefix

object FiltersConverter {
  def toCypher(
      filters: Filters,
      nodeVar: String
  ): String = {
    val idClause = filters.id.map(id => s"$nodeVar.id = '$id'")
    val idsClause = filters.ids.map(ids => s"$nodeVar.id IN ${ids.asJson}")
    val nameClause =
      filters.name.map(nameFilter =>
        StringFilterConverter.toCypher(
          nameFilter,
          s"${lowerPrefix}name",
          nodeVar
        )
      )
    val emailClause =
      filters.email.map(emailFilter =>
        StringFilterConverter.toCypher(
          emailFilter,
          s"${lowerPrefix}email",
          nodeVar
        )
      )
    val aliasesOrNameClause = filters.aliasesOrName.map(aliasesList =>
      s"(ANY(alias IN $nodeVar.aliases WHERE alias IN ${aliasesList.asJson}) OR " +
        s"ANY(searchTerm IN ${aliasesList.asJson} WHERE $nodeVar.name CONTAINS searchTerm))"
    )

    val prepTimeClause = filters.prepTime.map(prepTimeFilter =>
      NumberFilterConverter.toCypher(prepTimeFilter, "prepTime", nodeVar)
    )
    val cookTimeClause = filters.cookTime.map(cookTimeFilter =>
      NumberFilterConverter.toCypher(cookTimeFilter, "cookTime", nodeVar)
    )
    val vegetarianClause =
      filters.vegetarian.map(vegetarian => s"$nodeVar.vegetarian = '$vegetarian'")
    val veganClause = filters.vegan.map(vegan => s"$nodeVar.vegan = '$vegan'")
    val publicClause =
      filters.public.map(public => s"$nodeVar.public = $public")

    val tagsClause = filters.tags.map(tags =>
      tags
        .map(tag => s"MATCH ($nodeVar)-[:HAS_TAG]->(tag:$tag:Tag)")
        .mkString("\n")
    )
    val ingredientsClause = filters.ingredients.map(ingredients =>
      ingredients
        .map(ingredient =>
          s"MATCH ($nodeVar)-[:HAS_INGREDIENT]->(ingredient:$ingredient:Ingredient)"
        )
        .mkString("\n")
    )
    val notIngredientsClause = filters.notIngredients.map(notIngredients =>
      notIngredients
        .map(notIngredient =>
          s"MATCH ($nodeVar) WHERE NOT ($nodeVar)-[:HAS_INGREDIENT]->(notIngredient:$notIngredient:Ingredient)"
        )
        .mkString("\n")
    )
    val belongsToUserClause = filters.belongsToUser.map(id =>
      s"MATCH ($nodeVar)-[:BELONGS_TO]->(user:User) WHERE user.id = $id"
    )
    val savedByUserClause = filters.savedByUser.map(id =>
      s"MATCH ($nodeVar)-[:SAVED_BY]->(user:User) WHERE user.id = $id"
    )

    val matchingFilters = Seq(
      tagsClause,
      ingredientsClause,
      notIngredientsClause,
      belongsToUserClause,
      savedByUserClause
    )
    val nonMatchingFilters = Seq(
      idClause,
      idsClause,
      nameClause,
      emailClause,
      prepTimeClause,
      cookTimeClause,
      vegetarianClause,
      veganClause,
      publicClause,
      aliasesOrNameClause
    )

    matchingFilters
      .filter(_.isDefined)
      .map(_.get)
      .mkString(" \n ") + {
      if (nonMatchingFilters.exists(_.isDefined)) s"MATCH ($nodeVar) WHERE  "
      else ""
    } + nonMatchingFilters
      .filter(_.isDefined)
      .map(_.get)
      .mkString(" AND ")
  }
}
