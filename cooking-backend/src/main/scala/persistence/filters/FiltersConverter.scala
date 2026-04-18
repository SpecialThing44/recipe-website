package persistence.filters

import domain.filters.Filters
import io.circe.syntax.EncoderOps
import persistence.users.UserConverter.lowerPrefix
import persistence.filters.CypherScoringParts._

object FiltersConverter {
  private def ingredientMatchWithSubstitutesClause(
      nodeVar: String,
      ingredient: String,
      index: Int
  ): String = {
    val targetAlias = s"targetIngredient$index"
    val substituteAlias = s"substituteIngredient$index"
    val recipeIngredientAlias = s"recipeIngredient$index"
    s"""
       |MATCH ($targetAlias:Ingredient {name: '$ingredient'})
       |OPTIONAL MATCH ($targetAlias)-[:SUBSTITUTE]-($substituteAlias:Ingredient)
       |WITH $nodeVar, $targetAlias, collect(DISTINCT $substituteAlias) AS substituteIngredients$index
       |MATCH ($nodeVar)-[:HAS_INGREDIENT]->($recipeIngredientAlias:Ingredient)
       |WHERE $recipeIngredientAlias IN ([$targetAlias] + substituteIngredients$index)
       |WITH DISTINCT $nodeVar
       |""".stripMargin
  }

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

  private sealed trait SimilarityMode
  private case object RecipeRecipeMode extends SimilarityMode
  private case object UserRecipeMode extends SimilarityMode
  private case object UserUserMode extends SimilarityMode

  private def resolveSimilarityMode(
      filters: Filters,
      nodeVar: String
  ): Option[(SimilarityMode, String)] = {
    val isRecipeNode = nodeVar == "recipe"
    val isUserNode = nodeVar == "user"

    if (isRecipeNode && filters.analyzedRecipe.isDefined)
      Some(RecipeRecipeMode -> filters.analyzedRecipe.get.toString)
    else if (isRecipeNode && filters.analyzedUser.isDefined)
      Some(UserRecipeMode -> filters.analyzedUser.get.toString)
    else if (isUserNode && filters.analyzedUser.isDefined)
      Some(UserUserMode -> filters.analyzedUser.get.toString)
    else None
  }

  private def buildSimilarityStart(
      nodeVar: String,
      analyzedId: String,
      mode: SimilarityMode
  ): String = {
    val targetLabel = mode match {
      case RecipeRecipeMode => "Recipe"
      case UserRecipeMode   => "User"
      case UserUserMode     => "User"
    }
    val modeSpecificTail = mode match {
      case RecipeRecipeMode => s"\nWHERE $nodeVar.id <> target.id\n"
      case _                => ""
    }

    s"WITH $nodeVar\nMATCH (target:$targetLabel {id: '$analyzedId'})\n" + modeSpecificTail
  }

  private def buildModeFinalWhere(
      mode: SimilarityMode,
      nodeVar: String,
      analyzedId: String,
      ingredientActive: Boolean,
      coSaveActive: Boolean,
      tagActive: Boolean,
      ingredientMin: Option[Double],
      coSaveMin: Option[Double],
      tagMin: Option[Double]
  ): String =
    mode match {
      case RecipeRecipeMode => ""
      case UserRecipeMode =>
        val anyMinApplied = hasAppliedMinThreshold(
          ingredientActive -> ingredientMin,
          tagActive -> tagMin
        )
        val finalWhereOrAnd = if (anyMinApplied) "AND" else "WHERE"
        s"\n$finalWhereOrAnd $nodeVar.id IS NOT NULL \n"
      case UserUserMode =>
        val anyMinApplied = hasAppliedMinThreshold(
          ingredientActive -> ingredientMin,
          coSaveActive -> coSaveMin,
          tagActive -> tagMin
        )
        val finalWhereOrAnd = if (anyMinApplied) "AND" else "WHERE"
        s"\n$finalWhereOrAnd $nodeVar.id <> '$analyzedId'\n"
    }

  private def buildSimilarityCypher(
      base: String,
      mode: SimilarityMode,
      nodeVar: String,
      analyzedId: String,
      ingredientMin: Option[Double],
      coSaveMin: Option[Double],
      tagMin: Option[Double],
      ingredientActive: Boolean,
      coSaveActive: Boolean,
      tagActive: Boolean
  ): String = {
    val start = buildSimilarityStart(nodeVar, analyzedId, mode)

    val ingredientPartBuilder = mode match {
      case RecipeRecipeMode => recipeRecipeIngredientPart
      case UserRecipeMode   => userRecipeIngredientPart
      case UserUserMode     => userUserIngredientPart
    }

    val coSavePartBuilderOpt = mode match {
      case RecipeRecipeMode => Some(recipeRecipeCoSavePart)
      case UserRecipeMode   => None
      case UserUserMode     => Some(userUserCoSavePart)
    }

    val effectiveCoSaveActive = coSaveActive && coSavePartBuilderOpt.isDefined

    val tagPartBuilder = mode match {
      case RecipeRecipeMode => recipeRecipeTagPart
      case UserRecipeMode   => userRecipeTagPart
      case UserUserMode     => userUserTagPart
    }

    val ingredientPart =
      if (!ingredientActive) ""
      else ingredientPartBuilder(nodeVar, ingredientMin)

    val coSaveCarry = carryFields(
      nodeVar,
      includeIngredient = ingredientActive,
      includeCoSave = false
    )
    val coSavePart =
      if (!effectiveCoSaveActive) ""
      else coSavePartBuilderOpt.get(nodeVar, coSaveCarry, coSaveMin)

    val tagCarryBase = carryFields(
      nodeVar,
      includeIngredient = ingredientActive,
      includeCoSave = effectiveCoSaveActive
    )
    val tagPart =
      if (!tagActive) ""
      else tagPartBuilder(nodeVar, tagCarryBase, tagMin)

    val finalWhere = buildModeFinalWhere(
      mode,
      nodeVar,
      analyzedId,
      ingredientActive,
      effectiveCoSaveActive,
      tagActive,
      ingredientMin,
      coSaveMin,
      tagMin
    )

    val finalWith = buildFinalScoreWith(
      nodeVar,
      ingredientActive,
      effectiveCoSaveActive,
      tagActive
    )

    base + start + ingredientPart + coSavePart + tagPart + finalWhere + finalWith
  }

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
      s"ANY(searchTerm IN ${aliasesList.asJson} WHERE $nodeVar.lowername CONTAINS searchTerm OR " +
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
        .zipWithIndex
        .map { case (ingredient, index) =>
          ingredientMatchWithSubstitutesClause(nodeVar, ingredient, index)
        }
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

    val ingredientMin = filters.ingredientSimilarity.map(_.minScore)
    val coSaveMin = filters.coSaveSimilarity.map(_.minScore)
    val tagMin = filters.tagSimilarity.map(_.minScore)

    val ingredientActive = filters.ingredientSimilarity.isDefined
    val coSaveActive = filters.coSaveSimilarity.isDefined
    val tagActive = filters.tagSimilarity.isDefined

    resolveSimilarityMode(filters, nodeVar)
      .map { case (mode, analyzedId) =>
        buildSimilarityCypher(
          base,
          mode,
          nodeVar,
          analyzedId,
          ingredientMin,
          coSaveMin,
          tagMin,
          ingredientActive,
          coSaveActive,
          tagActive
        )
      }
      .getOrElse(base)
  }
}
