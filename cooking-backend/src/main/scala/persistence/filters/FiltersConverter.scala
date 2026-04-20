package persistence.filters

import domain.filters.Filters
import persistence.filters.CypherScoringParts.*
import persistence.users.UserConverter.lowerPrefix

import scala.jdk.CollectionConverters.SeqHasAsJava

object FiltersConverter {
  private def mergeParams(fragments: Seq[CypherFragment]): Map[String, AnyRef] =
    fragments.foldLeft(Map.empty[String, AnyRef])((acc, fragment) =>
      acc ++ fragment.params
    )

  private def nonEmpty(fragment: CypherFragment): Boolean =
    fragment.cypher.trim.nonEmpty

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

  private def similarityActive(filters: Filters): Boolean =
    (filters.ingredientSimilarity.isDefined || filters.coSaveSimilarity.isDefined || filters.tagSimilarity.isDefined) && (filters.analyzedRecipe.isDefined || filters.analyzedUser.isDefined)

  private def buildBaseCypher(
      matchingFilters: Seq[Option[CypherFragment]],
      nonMatchingFilters: Seq[Option[CypherFragment]],
      nodeVar: String
  ): CypherFragment = {
    val matching = matchingFilters.flatten.filter(nonEmpty)
    val nonMatching = nonMatchingFilters.flatten.filter(nonEmpty)

    val matchingCypher = matching.map(_.cypher).mkString(" \n ")
    val nonMatchingCypher = nonMatching.map(_.cypher).mkString(" AND ")
    val wherePrefix =
      if (nonMatching.nonEmpty) s"MATCH ($nodeVar) WHERE  " else ""

    CypherFragment(
      matchingCypher + wherePrefix + nonMatchingCypher,
      mergeParams(matching ++ nonMatching)
    )
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
      analyzedIdParam: String,
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

    s"WITH $nodeVar\nMATCH (target:$targetLabel {id: $$${analyzedIdParam}})\n" + modeSpecificTail
  }

  private def buildModeFinalWhere(
      mode: SimilarityMode,
      nodeVar: String,
      analyzedIdParam: String,
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
        s"\n$finalWhereOrAnd $nodeVar.id <> $$${analyzedIdParam}\n"
    }

  private def buildSimilarityCypher(
      base: CypherFragment,
      mode: SimilarityMode,
      nodeVar: String,
      analyzedIdParam: String,
      ingredientMin: Option[Double],
      coSaveMin: Option[Double],
      tagMin: Option[Double],
      ingredientMinParam: Option[String],
      coSaveMinParam: Option[String],
      tagMinParam: Option[String],
      ingredientActive: Boolean,
      coSaveActive: Boolean,
      tagActive: Boolean
  ): CypherFragment = {
    val start = buildSimilarityStart(nodeVar, analyzedIdParam, mode)

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
      else ingredientPartBuilder(nodeVar, ingredientMinParam)

    val coSaveCarry = carryFields(
      nodeVar,
      includeIngredient = ingredientActive,
      includeCoSave = false
    )
    val coSavePart =
      if (!effectiveCoSaveActive) ""
      else coSavePartBuilderOpt.get(nodeVar, coSaveCarry, coSaveMinParam)

    val tagCarryBase = carryFields(
      nodeVar,
      includeIngredient = ingredientActive,
      includeCoSave = effectiveCoSaveActive
    )
    val tagPart =
      if (!tagActive) ""
      else tagPartBuilder(nodeVar, tagCarryBase, tagMinParam)

    val finalWhere = buildModeFinalWhere(
      mode,
      nodeVar,
      analyzedIdParam,
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

    CypherFragment(
      base.cypher + start + ingredientPart + coSavePart + tagPart + finalWhere + finalWith,
      base.params
    )
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

  def limitAndSkipStatement(filters: Filters): CypherFragment =
    filters.limit
      .map(limitValue => {
        val pageValue = filters.page.getOrElse(0)
        val limitParam = "filter_limit"
        val skipParam = "filter_skip"
        CypherFragment(
          s"SKIP $$${skipParam} LIMIT $$${limitParam}",
          Map(
            skipParam -> Int.box(pageValue * limitValue),
            limitParam -> Int.box(limitValue)
          )
        )
      })
      .getOrElse(CypherFragment.empty)

  def toCypher(
      filters: Filters,
      nodeVar: String
  ): CypherFragment = {
    val idClause = filters.id.map(id => {
      val param = s"${nodeVar}_id"
      CypherFragment(s"$nodeVar.id = $$${param}", Map(param -> id.toString))
    })

    val idsClause = filters.ids.map(ids => {
      val param = s"${nodeVar}_ids"
      CypherFragment(
        s"$nodeVar.id IN $$${param}",
        Map(param -> ids.map(_.toString).asJava)
      )
    })

    val nameClause =
      filters.name.map(nameFilter =>
        StringFilterConverter.toCypher(
          nameFilter,
          s"${lowerPrefix}name",
          nodeVar,
          s"${nodeVar}_name"
        )
      )

    val emailClause =
      filters.email.map(emailFilter =>
        StringFilterConverter.toCypher(
          emailFilter,
          s"${lowerPrefix}email",
          nodeVar,
          s"${nodeVar}_email"
        )
      )

    val aliasesOrNameClause = filters.aliasesOrName.map(aliasesList => {
      val param = s"${nodeVar}_aliases_or_name"
      CypherFragment(
        s"ANY(searchTerm IN $$${param} WHERE $nodeVar.lowername CONTAINS searchTerm OR " +
          s"($nodeVar.aliases IS NOT NULL AND ANY(alias IN $nodeVar.aliases WHERE alias CONTAINS searchTerm)))",
        Map(param -> aliasesList.map(_.toLowerCase).asJava)
      )
    })

    val prepTimeClause = filters.prepTime.map(prepTimeFilter =>
      NumberFilterConverter.toCypher(
        prepTimeFilter,
        "prepTime",
        nodeVar,
        s"${nodeVar}_prepTime"
      )
    )

    val cookTimeClause = filters.cookTime.map(cookTimeFilter =>
      NumberFilterConverter.toCypher(
        cookTimeFilter,
        "cookTime",
        nodeVar,
        s"${nodeVar}_cookTime"
      )
    )

    val tagsClause = filters.tags.map(tags => {
      val tagClauses = tags.zipWithIndex.map { case (tag, index) =>
        val param = s"${nodeVar}_tag_$index"
        val tagAlias = s"tagFilter$index"
        CypherFragment(
          s"MATCH ($nodeVar)-[:HAS_TAG]->($tagAlias:Tag {lowername: $$${param}})",
          Map(param -> tag.toLowerCase)
        )
      }
      CypherFragment(
        tagClauses.map(_.cypher).mkString("\n"),
        mergeParams(tagClauses)
      )
    })

    val ingredientsClause = filters.ingredients.map(ingredients => {
      val ingredientClauses = ingredients.zipWithIndex
        .map { case (ingredient, index) =>
          ingredientMatchWithSubstitutesClause(nodeVar, ingredient, index)
        }
      CypherFragment(
        ingredientClauses.map(_.cypher).mkString("\n"),
        mergeParams(ingredientClauses)
      )
    })

    val notIngredientsClause = filters.notIngredients.map(notIngredients => {
      val clauses = notIngredients.zipWithIndex.map {
        case (notIngredient, index) =>
          val param = s"${nodeVar}_not_ingredient_$index"
          CypherFragment(
            s"MATCH ($nodeVar) WHERE NOT ($nodeVar)-[:HAS_INGREDIENT]->(:Ingredient {lowername: $$${param}})",
            Map(param -> notIngredient.toLowerCase)
          )
      }
      CypherFragment(clauses.map(_.cypher).mkString("\n"), mergeParams(clauses))
    })

    val belongsToUserClause = filters.belongsToUser.map(id => {
      val param = s"${nodeVar}_belongs_to_user"
      CypherFragment(
        s"MATCH ($nodeVar)-[:BELONGS_TO|CREATED_BY]->(belongsToUser:User) WHERE belongsToUser.id = $$${param}",
        Map(param -> id.toString)
      )
    })

    val savedByUserClause = filters.savedByUser.map(id => {
      val param = s"${nodeVar}_saved_by_user"
      CypherFragment(
        s"MATCH ($nodeVar)-[:SAVED_BY]->(savedUser:User) WHERE savedUser.id = $$${param}",
        Map(param -> id.toString)
      )
    })

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
      aliasesOrNameClause
    )

    val base = buildBaseCypher(matchingFilters, nonMatchingFilters, nodeVar)

    val ingredientMin = filters.ingredientSimilarity.map(_.minScore)
    val coSaveMin = filters.coSaveSimilarity.map(_.minScore)
    val tagMin = filters.tagSimilarity.map(_.minScore)

    val ingredientActive = filters.ingredientSimilarity.isDefined
    val coSaveActive = filters.coSaveSimilarity.isDefined
    val tagActive = filters.tagSimilarity.isDefined

    val ingredientMinParam =
      ingredientMin.map(_ => s"${nodeVar}_ingredient_similarity_min")
    val coSaveMinParam =
      coSaveMin.map(_ => s"${nodeVar}_co_save_similarity_min")
    val tagMinParam = tagMin.map(_ => s"${nodeVar}_tag_similarity_min")

    resolveSimilarityMode(filters, nodeVar)
      .map { case (mode, analyzedId) =>
        val analyzedIdParam = s"${nodeVar}_analyzed_id"
        val similarityParams = Map(
          analyzedIdParam -> analyzedId
        ) ++ ingredientMinParam
          .zip(ingredientMin)
          .map { case (param, value) =>
            param -> Double.box(value)
          } ++ coSaveMinParam
          .zip(coSaveMin)
          .map { case (param, value) =>
            param -> Double.box(value)
          } ++ tagMinParam
          .zip(tagMin)
          .map { case (param, value) => param -> Double.box(value) }

        buildSimilarityCypher(
          base = CypherFragment(base.cypher, base.params ++ similarityParams),
          mode = mode,
          nodeVar = nodeVar,
          analyzedIdParam = analyzedIdParam,
          ingredientMin = ingredientMin,
          coSaveMin = coSaveMin,
          tagMin = tagMin,
          ingredientMinParam = ingredientMinParam,
          coSaveMinParam = coSaveMinParam,
          tagMinParam = tagMinParam,
          ingredientActive = ingredientActive,
          coSaveActive = coSaveActive,
          tagActive = tagActive
        )
      }
      .getOrElse(base)
  }
}
