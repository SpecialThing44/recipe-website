package persistence.filters.scoring

import domain.filters.Filters
import persistence.filters.CypherFragment
import persistence.filters.scoring.CoSaveScoringParts.*
import persistence.filters.scoring.IngredientScoringParts.*
import persistence.filters.scoring.TagScoringParts.*

object SimilarityQueryBuilder {
  private sealed trait SimilarityMode
  private case object RecipeRecipeMode extends SimilarityMode
  private case object UserRecipeMode extends SimilarityMode
  private case object UserUserMode extends SimilarityMode

  private type IngredientPartBuilder = (String, Option[String]) => String
  private type CoSavePartBuilder = (String, String, Option[String]) => String
  private type TagPartBuilder = (String, String, Option[String]) => String

  private final case class ModeBuilders(
      targetLabel: String,
      appendStartExclusion: Boolean,
      ingredientPartBuilder: IngredientPartBuilder,
      coSavePartBuilder: Option[CoSavePartBuilder],
      tagPartBuilder: TagPartBuilder
  )

  private final case class SimilarityFlags(
      ingredientMin: Option[Double],
      coSaveMin: Option[Double],
      tagMin: Option[Double],
      ingredientActive: Boolean,
      coSaveActive: Boolean,
      tagActive: Boolean,
      ingredientMinParam: Option[String],
      coSaveMinParam: Option[String],
      tagMinParam: Option[String]
  )

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
    val scoreParts =
      (if (ingredientActive) Seq("ingredientScore") else Seq()) ++
        (if (coSaveActive) Seq("coSaveScore") else Seq()) ++
        (if (tagActive) Seq("tagScore") else Seq())

    val sumExpr = if (scoreParts.isEmpty) "0.0" else scoreParts.mkString(" + ")
    val denom = scoreParts.size.max(1)
    s"WITH $nodeVar, ($sumExpr) / $denom AS score\n"
  }

  private def hasAppliedMinThreshold(
      activeAndMin: (Boolean, Option[Double])*
  ): Boolean =
    activeAndMin.exists { case (active, min) => active && min.isDefined }

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

  private def modeBuilders(mode: SimilarityMode): ModeBuilders =
    mode match {
      case RecipeRecipeMode =>
        ModeBuilders(
          targetLabel = "Recipe",
          appendStartExclusion = true,
          ingredientPartBuilder = recipeRecipeIngredientPart,
          coSavePartBuilder = Some(recipeRecipeCoSavePart),
          tagPartBuilder = recipeRecipeTagPart
        )
      case UserRecipeMode =>
        ModeBuilders(
          targetLabel = "User",
          appendStartExclusion = false,
          ingredientPartBuilder = userRecipeIngredientPart,
          coSavePartBuilder = None,
          tagPartBuilder = userRecipeTagPart
        )
      case UserUserMode =>
        ModeBuilders(
          targetLabel = "User",
          appendStartExclusion = false,
          ingredientPartBuilder = userUserIngredientPart,
          coSavePartBuilder = Some(userUserCoSavePart),
          tagPartBuilder = userUserTagPart
        )
    }

  private def buildSimilarityStart(
      nodeVar: String,
      analyzedIdParam: String,
      mode: SimilarityMode
  ): String = {
    val builders = modeBuilders(mode)
    val modeSpecificTail =
      if (builders.appendStartExclusion) s"\nWHERE $nodeVar.id <> target.id\n"
      else ""

    s"WITH $nodeVar\nMATCH (target:${builders.targetLabel} {id: $$${analyzedIdParam}})\n" + modeSpecificTail
  }

  private def buildModeFinalWhere(
      mode: SimilarityMode,
      nodeVar: String,
      analyzedIdParam: String,
      flags: SimilarityFlags,
      effectiveCoSaveActive: Boolean
  ): String =
    mode match {
      case RecipeRecipeMode => ""
      case UserRecipeMode =>
        val anyMinApplied = hasAppliedMinThreshold(
          flags.ingredientActive -> flags.ingredientMin,
          flags.tagActive -> flags.tagMin
        )
        val finalWhereOrAnd = if (anyMinApplied) "AND" else "WHERE"
        s"\n$finalWhereOrAnd $nodeVar.id IS NOT NULL \n"
      case UserUserMode =>
        val anyMinApplied = hasAppliedMinThreshold(
          flags.ingredientActive -> flags.ingredientMin,
          effectiveCoSaveActive -> flags.coSaveMin,
          flags.tagActive -> flags.tagMin
        )
        val finalWhereOrAnd = if (anyMinApplied) "AND" else "WHERE"
        s"\n$finalWhereOrAnd $nodeVar.id <> $$${analyzedIdParam}\n"
    }

  private def addOptionalDoubleParam(
      params: Map[String, AnyRef],
      paramName: Option[String],
      value: Option[Double]
  ): Map[String, AnyRef] =
    paramName
      .zip(value)
      .foldLeft(params) { case (acc, (name, v)) =>
        acc + (name -> Double.box(v))
      }

  private def buildSimilarityParams(
      analyzedIdParam: String,
      analyzedId: String,
      flags: SimilarityFlags
  ): Map[String, AnyRef] = {
    val withAnalyzedId = Map(analyzedIdParam -> analyzedId)
    val withIngredient = addOptionalDoubleParam(
      withAnalyzedId,
      flags.ingredientMinParam,
      flags.ingredientMin
    )
    val withCoSave =
      addOptionalDoubleParam(withIngredient, flags.coSaveMinParam, flags.coSaveMin)

    addOptionalDoubleParam(withCoSave, flags.tagMinParam, flags.tagMin)
  }

  private def buildSimilarityCypher(
      base: CypherFragment,
      mode: SimilarityMode,
      nodeVar: String,
      analyzedIdParam: String,
      flags: SimilarityFlags
  ): CypherFragment = {
    val builders = modeBuilders(mode)

    val start = buildSimilarityStart(nodeVar, analyzedIdParam, mode)

    val ingredientPart =
      if (!flags.ingredientActive) ""
      else builders.ingredientPartBuilder(nodeVar, flags.ingredientMinParam)

    val effectiveCoSaveActive =
      flags.coSaveActive && builders.coSavePartBuilder.isDefined

    val coSaveCarry = carryFields(
      nodeVar,
      includeIngredient = flags.ingredientActive,
      includeCoSave = false
    )
    val coSavePart =
      if (!effectiveCoSaveActive) ""
      else
        builders.coSavePartBuilder.get(nodeVar, coSaveCarry, flags.coSaveMinParam)

    val tagCarryBase = carryFields(
      nodeVar,
      includeIngredient = flags.ingredientActive,
      includeCoSave = effectiveCoSaveActive
    )
    val tagPart =
      if (!flags.tagActive) ""
      else builders.tagPartBuilder(nodeVar, tagCarryBase, flags.tagMinParam)

    val finalWhere = buildModeFinalWhere(
      mode,
      nodeVar,
      analyzedIdParam,
      flags,
      effectiveCoSaveActive
    )

    val finalWith = buildFinalScoreWith(
      nodeVar,
      flags.ingredientActive,
      effectiveCoSaveActive,
      flags.tagActive
    )

    CypherFragment(
      base.cypher + start + ingredientPart + coSavePart + tagPart + finalWhere + finalWith,
      base.params
    )
  }

  private def similarityFlags(filters: Filters, nodeVar: String): SimilarityFlags = {
    val ingredientMin = filters.ingredientSimilarity.map(_.minScore)
    val coSaveMin = filters.coSaveSimilarity.map(_.minScore)
    val tagMin = filters.tagSimilarity.map(_.minScore)

    SimilarityFlags(
      ingredientMin = ingredientMin,
      coSaveMin = coSaveMin,
      tagMin = tagMin,
      ingredientActive = filters.ingredientSimilarity.isDefined,
      coSaveActive = filters.coSaveSimilarity.isDefined,
      tagActive = filters.tagSimilarity.isDefined,
      ingredientMinParam = ingredientMin.map(_ => s"${nodeVar}_ingredient_similarity_min"),
      coSaveMinParam = coSaveMin.map(_ => s"${nodeVar}_co_save_similarity_min"),
      tagMinParam = tagMin.map(_ => s"${nodeVar}_tag_similarity_min")
    )
  }

  def apply(base: CypherFragment, filters: Filters, nodeVar: String): CypherFragment = {
    val flags = similarityFlags(filters, nodeVar)

    resolveSimilarityMode(filters, nodeVar)
      .map { case (mode, analyzedId) =>
        val analyzedIdParam = s"${nodeVar}_analyzed_id"
        val similarityParams = buildSimilarityParams(analyzedIdParam, analyzedId, flags)

        buildSimilarityCypher(
          base = CypherFragment(base.cypher, base.params ++ similarityParams),
          mode = mode,
          nodeVar = nodeVar,
          analyzedIdParam = analyzedIdParam,
          flags = flags
        )
      }
      .getOrElse(base)
  }
}
