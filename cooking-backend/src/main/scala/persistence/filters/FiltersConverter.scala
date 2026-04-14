package persistence.filters

import domain.filters.Filters
import io.circe.syntax.EncoderOps
import persistence.users.UserConverter.lowerPrefix
import persistence.filters.CypherScoringParts._

object FiltersConverter {
  private def similarityActive(filters: Filters): Boolean =
    (filters.ingredientSimilarity.isDefined || filters.coSaveSimilarity.isDefined || filters.tagSimilarity.isDefined) && (filters.analyzedRecipe.isDefined || filters.analyzedUser.isDefined)

  private def buildBaseCypher(
      matchingFilters: Seq[Option[String]],
      nonMatchingFilters: Seq[Option[String]],
      nodeVar: String
  ): String = {
    matchingFilters.flatten.mkString(" \n ") + {
      if (nonMatchingFilters.exists(_.isDefined)) s"MATCH ($nodeVar) WHERE  "
      else ""
    } + nonMatchingFilters.flatten.mkString(" AND ")
  }

  private def carryFields(
      nodeVar: String,
      includeIngredient: Boolean,
      includeCoSave: Boolean
  ): String =
    if (includeIngredient && includeCoSave)
      s"$nodeVar, target, ingredientScore, coSaveScore"
    else if (includeIngredient) s"$nodeVar, target, ingredientScore"
    else if (includeCoSave) s"$nodeVar, target, coSaveScore"
    else s"$nodeVar, target"

  private def buildFinalScoreWith(
      nodeVar: String,
      ingredientActive: Boolean,
      coSaveActive: Boolean,
      tagActive: Boolean
  ): String = {
    val scoreParts = (if (ingredientActive) Seq("ingredientScore")
                      else Seq()) ++
      (if (coSaveActive) Seq("coSaveScore")
       else Seq()) ++
      (if (tagActive) Seq("tagScore")
       else Seq())
    val sumExpr = if (scoreParts.isEmpty) "0.0" else scoreParts.mkString(" + ")
    val denom = scoreParts.size.max(1)
    s"WITH $nodeVar, ($sumExpr) / $denom AS score\n"
  }

  private def hasAppliedMinThreshold(
      activeAndMin: (Boolean, Option[Double])*
  ): Boolean =
    activeAndMin.exists { case (active, min) => active && min.isDefined }

  def getOrderLine(filters: Filters, nodeVar: String): String = {
    if (similarityActive(filters)) {
      "ORDER BY score DESC"
    } else if (
      filters.orderBy.isDefined && filters.orderBy.get.name.isDefined
    ) {
      s"ORDER BY $nodeVar.name"
    } else {
      ""
    }
  }
  def getWithScoreLine(filters: Filters, withStatement: String): String =
    if (similarityActive(filters)) withStatement + ", score" else withStatement

  def limitAndSkipStatement(filters: Filters): String =
    filters.limit
      .map(l => s"SKIP ${filters.page.getOrElse(0) * l} LIMIT $l")
      .getOrElse("")

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
      s"ANY(searchTerm IN ${aliasesList.asJson} WHERE $nodeVar.name CONTAINS searchTerm OR " +
        s"($nodeVar.aliases IS NOT NULL AND ANY(alias IN $nodeVar.aliases WHERE alias CONTAINS searchTerm)))"
    )

    val prepTimeClause = filters.prepTime.map(prepTimeFilter =>
      NumberFilterConverter.toCypher(prepTimeFilter, "prepTime", nodeVar)
    )
    val cookTimeClause = filters.cookTime.map(cookTimeFilter =>
      NumberFilterConverter.toCypher(cookTimeFilter, "cookTime", nodeVar)
    )
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
          s"MATCH ($nodeVar)-[:HAS_INGREDIENT]->(hasIngredient:Ingredient {name: '$ingredient'})"
        )
        .mkString("\n")
    )
    val notIngredientsClause = filters.notIngredients.map(notIngredients =>
      notIngredients
        .map(notIngredient =>
          s"MATCH ($nodeVar) WHERE NOT ($nodeVar)-[:HAS_INGREDIENT]->(:Ingredient {name: '$notIngredient'})"
        )
        .mkString("\n")
    )
    val belongsToUserClause = filters.belongsToUser.map(id =>
      s"MATCH ($nodeVar)-[:BELONGS_TO|CREATED_BY]->(belongsToUser:User) WHERE belongsToUser.id = '$id'"
    )
    val savedByUserClause = filters.savedByUser.map(id =>
      s"MATCH ($nodeVar)-[:SAVED_BY]->(savedUser:User) WHERE savedUser.id = '$id'"
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
      publicClause,
      aliasesOrNameClause
    )

    val base = buildBaseCypher(matchingFilters, nonMatchingFilters, nodeVar)

    val isRecipeNode = nodeVar == "recipe"
    val isUserNode = nodeVar == "user"

    val ingredientMin = filters.ingredientSimilarity.map(_.minScore)
    val coSaveMin = filters.coSaveSimilarity.map(_.minScore)
    val tagMin = filters.tagSimilarity.map(_.minScore)

    val mode =
      if (isRecipeNode && filters.analyzedRecipe.isDefined) "RecipeRecipe"
      else if (isRecipeNode && filters.analyzedUser.isDefined) "UserRecipe"
      else if (isUserNode && filters.analyzedUser.isDefined) "UserUser"
      else "None"

    if (mode == "None") base
    else if (mode == "RecipeRecipe") {
      val analyzedId = filters.analyzedRecipe.get
      val ingredientActiveRecipe = filters.ingredientSimilarity.isDefined
      val coSaveActiveRecipe = filters.coSaveSimilarity.isDefined
      val tagActiveRecipe = filters.tagSimilarity.isDefined

      val start = s"""
         |WITH $nodeVar
         |MATCH (target:Recipe {id: '$analyzedId'})
         |WHERE $nodeVar.id <> target.id
         |""".stripMargin

      val ingredientPart =
        if (!ingredientActiveRecipe) ""
        else recipeRecipeIngredientPart(nodeVar, ingredientMin)

      val coSaveCarry = carryFields(
        nodeVar,
        includeIngredient = ingredientActiveRecipe,
        includeCoSave = false
      )
      val coSavePart =
        if (!coSaveActiveRecipe) ""
        else recipeRecipeCoSavePart(nodeVar, coSaveCarry, coSaveMin)

      val tagCarryBase = carryFields(
        nodeVar,
        includeIngredient = ingredientActiveRecipe,
        includeCoSave = coSaveActiveRecipe
      )
      val tagPart =
        if (!tagActiveRecipe) ""
        else recipeRecipeTagPart(nodeVar, tagCarryBase, tagMin)

      val finalWith = buildFinalScoreWith(
        nodeVar,
        ingredientActiveRecipe,
        coSaveActiveRecipe,
        tagActiveRecipe
      )

      base + start + ingredientPart + coSavePart + tagPart + finalWith
    } else if (mode == "UserRecipe") {
      val analyzedId = filters.analyzedUser.get
      val ingredientActiveRecipe = filters.ingredientSimilarity.isDefined
      val tagActiveRecipe = filters.tagSimilarity.isDefined

      val start = s"""
         |WITH $nodeVar
         |MATCH (target:User {id: '$analyzedId'})
         |""".stripMargin

      val ingredientPart =
        if (!ingredientActiveRecipe) ""
        else userRecipeIngredientPart(nodeVar, ingredientMin)

      val tagCarryBase = carryFields(
        nodeVar,
        includeIngredient = ingredientActiveRecipe,
        includeCoSave = false
      )

      val tagPart =
        if (!tagActiveRecipe) ""
        else userRecipeTagPart(nodeVar, tagCarryBase, tagMin)

      val anyMinApplied = hasAppliedMinThreshold(
        ingredientActiveRecipe -> ingredientMin,
        tagActiveRecipe -> tagMin
      )
      val finalWhereOrAnd = if (anyMinApplied) "AND" else "WHERE"
      val finalWhere = s"\n$finalWhereOrAnd $nodeVar.id IS NOT NULL \n"

      val finalWith = buildFinalScoreWith(
        nodeVar,
        ingredientActiveRecipe,
        coSaveActive = false,
        tagActiveRecipe
      )

      base + start + ingredientPart + tagPart + finalWhere + finalWith

    } else {
      val analyzedId = filters.analyzedUser.get
      val ingredientActiveUser = filters.ingredientSimilarity.isDefined
      val coSaveActiveUser = filters.coSaveSimilarity.isDefined
      val tagActiveUser = filters.tagSimilarity.isDefined

      val start = s"""
         |WITH $nodeVar
         |MATCH (target:User {id: '$analyzedId'})
         |""".stripMargin

      val ingredientPart =
        if (!ingredientActiveUser) ""
        else userUserIngredientPart(nodeVar, ingredientMin)

      val coSaveCarry = carryFields(
        nodeVar,
        includeIngredient = ingredientActiveUser,
        includeCoSave = false
      )
      val coSavePart =
        if (!coSaveActiveUser) ""
        else userUserCoSavePart(nodeVar, coSaveCarry, coSaveMin)

      val tagCarryBase = carryFields(
        nodeVar,
        includeIngredient = ingredientActiveUser,
        includeCoSave = coSaveActiveUser
      )
      val tagPart =
        if (!tagActiveUser) ""
        else userUserTagPart(nodeVar, tagCarryBase, tagMin)

      val anyMinApplied = hasAppliedMinThreshold(
        ingredientActiveUser -> ingredientMin,
        coSaveActiveUser -> coSaveMin,
        tagActiveUser -> tagMin
      )
      val finalWhereOrAnd = if (anyMinApplied) "AND" else "WHERE"
      val finalWhere = s"\n$finalWhereOrAnd $nodeVar.id <> '$analyzedId'\n"

      val finalWith = buildFinalScoreWith(
        nodeVar,
        ingredientActiveUser,
        coSaveActiveUser,
        tagActiveUser
      )

      base + start + ingredientPart + coSavePart + tagPart + finalWhere + finalWith
    }
  }
}
