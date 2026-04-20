package persistence.filters.base

import domain.filters.Filters
import persistence.filters.CypherFragment
import persistence.filters.CypherSupport.mergeParams

object FiltersMatchingClauses {
  private def indexedClauses[A](
      values: Seq[A],
      make: (A, Int) => CypherFragment
  ): CypherFragment = {
    val clauses = values.zipWithIndex.map { case (value, index) =>
      make(value, index)
    }
    CypherFragment(clauses.map(_.cypher).mkString("\n"), mergeParams(clauses))
  }

  private def ingredientMatchWithSubstitutesClause(
      nodeVar: String,
      ingredient: String,
      index: Int
  ): CypherFragment = {
    val targetAlias = s"targetIngredient$index"
    val substituteAlias = s"substituteIngredient$index"
    val recipeIngredientAlias = s"recipeIngredient$index"
    val ingredientParam = s"${nodeVar}_ingredient_$index"
    CypherFragment(
      s"""
         |MATCH ($targetAlias:Ingredient {lowername: $$${ingredientParam}})
         |OPTIONAL MATCH ($targetAlias)-[:SUBSTITUTE]-($substituteAlias:Ingredient)
         |WITH $nodeVar, $targetAlias, collect(DISTINCT $substituteAlias) AS substituteIngredients$index
         |MATCH ($nodeVar)-[:HAS_INGREDIENT]->($recipeIngredientAlias:Ingredient)
         |WHERE $recipeIngredientAlias IN ([$targetAlias] + substituteIngredients$index)
         |WITH DISTINCT $nodeVar
         |""".stripMargin,
      Map(ingredientParam -> ingredient.toLowerCase)
    )
  }

  private def tagsClause(
      filters: Filters,
      nodeVar: String
  ): Option[CypherFragment] =
    filters.tags.map(tags =>
      indexedClauses(
        tags,
        (tag: String, index: Int) => {
          val param = s"${nodeVar}_tag_$index"
          val tagAlias = s"tagFilter$index"
          CypherFragment(
            s"MATCH ($nodeVar)-[:HAS_TAG]->($tagAlias:Tag {lowername: $$${param}})",
            Map(param -> tag.toLowerCase)
          )
        }
      )
    )

  private def ingredientsClause(
      filters: Filters,
      nodeVar: String
  ): Option[CypherFragment] =
    filters.ingredients.map(ingredients =>
      indexedClauses(
        ingredients,
        (ingredient: String, index: Int) =>
          ingredientMatchWithSubstitutesClause(nodeVar, ingredient, index)
      )
    )

  private def notIngredientsClause(
      filters: Filters,
      nodeVar: String
  ): Option[CypherFragment] =
    filters.notIngredients.map(notIngredients =>
      indexedClauses(
        notIngredients,
        (notIngredient: String, index: Int) => {
          val param = s"${nodeVar}_not_ingredient_$index"
          CypherFragment(
            s"MATCH ($nodeVar) WHERE NOT ($nodeVar)-[:HAS_INGREDIENT]->(:Ingredient {lowername: $$${param}})",
            Map(param -> notIngredient.toLowerCase)
          )
        }
      )
    )

  private def belongsToUserClause(
      filters: Filters,
      nodeVar: String
  ): Option[CypherFragment] =
    filters.belongsToUser.map(id => {
      val param = s"${nodeVar}_belongs_to_user"
      CypherFragment(
        s"MATCH ($nodeVar)-[:BELONGS_TO|CREATED_BY]->(belongsToUser:User) WHERE belongsToUser.id = $$${param}",
        Map(param -> id.toString)
      )
    })

  private def savedByUserClause(
      filters: Filters,
      nodeVar: String
  ): Option[CypherFragment] =
    filters.savedByUser.map(id => {
      val param = s"${nodeVar}_saved_by_user"
      CypherFragment(
        s"MATCH ($nodeVar)-[:SAVED_BY]->(savedUser:User) WHERE savedUser.id = $$${param}",
        Map(param -> id.toString)
      )
    })

  def matchingFilters(
      filters: Filters,
      nodeVar: String
  ): Seq[Option[CypherFragment]] =
    Seq(
      tagsClause(filters, nodeVar),
      ingredientsClause(filters, nodeVar),
      notIngredientsClause(filters, nodeVar),
      belongsToUserClause(filters, nodeVar),
      savedByUserClause(filters, nodeVar)
    )

}
