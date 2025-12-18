package persistence.filters

import domain.filters.Filters
import io.circe.syntax.EncoderOps
import persistence.users.UserConverter.lowerPrefix

object FiltersConverter {
  def similarityActive(filters: Filters): Boolean =
    (filters.ingredientSimilarity.isDefined || filters.coSaveSimilarity.isDefined || filters.tagSimilarity.isDefined) && filters.analyzedEntity.isDefined
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
      s"(((EXISTS($nodeVar.aliases) AND ANY(alias IN $nodeVar.aliases WHERE alias IN ${aliasesList.asJson}))) OR " +
        s"ANY(searchTerm IN ${aliasesList.asJson} WHERE $nodeVar.name CONTAINS searchTerm))"
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

    val base = matchingFilters
      .filter(_.isDefined)
      .map(_.get)
      .mkString(" \n ") + {
      if (nonMatchingFilters.exists(_.isDefined)) s"MATCH ($nodeVar) WHERE  "
      else ""
    } + nonMatchingFilters
      .filter(_.isDefined)
      .map(_.get)
      .mkString(" AND ")

    val isRecipeNode = nodeVar == "recipe"
    val isUserNode = nodeVar == "user"
    val ingredientActiveRecipe =
      filters.ingredientSimilarity.isDefined && filters.analyzedEntity.isDefined && isRecipeNode
    val coSaveActiveRecipe =
      filters.coSaveSimilarity.isDefined && filters.analyzedEntity.isDefined && isRecipeNode
    val tagActiveRecipe =
      filters.tagSimilarity.isDefined && filters.analyzedEntity.isDefined && isRecipeNode

    val ingredientActiveUser =
      filters.ingredientSimilarity.isDefined && filters.analyzedEntity.isDefined && isUserNode
    val coSaveActiveUser =
      filters.coSaveSimilarity.isDefined && filters.analyzedEntity.isDefined && isUserNode
    val tagActiveUser =
      filters.tagSimilarity.isDefined && filters.analyzedEntity.isDefined && isUserNode

    if (
      !ingredientActiveRecipe && !coSaveActiveRecipe && !tagActiveRecipe && !ingredientActiveUser && !coSaveActiveUser && !tagActiveUser
    ) base
    else if (isRecipeNode) {
      val analyzedId = filters.analyzedEntity.get
      val ingredientMin = filters.ingredientSimilarity.map(_.minScore)
      val coSaveMin = filters.coSaveSimilarity.map(_.minScore)
      val tagMin = filters.tagSimilarity.map(_.minScore)

      val start = s"""
         |WITH $nodeVar
         |MATCH (target:Recipe {id: '$analyzedId'})
         |""".stripMargin

      val ingredientPart =
        if (!ingredientActiveRecipe) ""
        else
          s"""
           |MATCH (target)-[tr:HAS_INGREDIENT]->(ti:Ingredient)
           |WITH $nodeVar, target, collect({ingredientId: ti.id, weight: coalesce(tr.normalizedWeight, 0.0)}) AS targetVector
           |MATCH ($nodeVar)-[ci:HAS_INGREDIENT]->(ciIngr:Ingredient)
           |WITH $nodeVar, target, targetVector, collect({ingredientId: ciIngr.id, weight: coalesce(ci.normalizedWeight, 0.0)}) AS candidateVector
           |WITH $nodeVar, target, targetVector, candidateVector,
           |     reduce(dot = 0.0, x IN targetVector | dot + coalesce([y IN candidateVector WHERE y.ingredientId = x.ingredientId][0].weight, 0.0) * x.weight) AS dotProd,
           |     sqrt(reduce(s = 0.0, x IN targetVector | s + x.weight * x.weight)) AS normTarget,
           |     sqrt(reduce(s = 0.0, x IN candidateVector | s + x.weight * x.weight)) AS normCandidate
           |WITH $nodeVar, target, CASE WHEN normTarget = 0 OR normCandidate = 0 THEN 0.0 ELSE dotProd / (normTarget * normCandidate) END AS ingredientScore
           |""".stripMargin + ingredientMin
            .map(min => s"\nWHERE ingredientScore >= $min")
            .getOrElse("")

      val coSaveCarry =
        if (ingredientActiveRecipe) s"$nodeVar, target, ingredientScore"
        else s"$nodeVar, target"
      val coSavePart =
        if (!coSaveActiveRecipe) ""
        else
          s"""
           |WITH $coSaveCarry
           |OPTIONAL MATCH (target)-[:SAVED_BY]->(ur:User)
           |WITH $coSaveCarry, collect(DISTINCT ur.id) AS usersT
           |OPTIONAL MATCH ($nodeVar)-[:SAVED_BY]->(uc:User)
           |WITH $coSaveCarry, usersT, collect(DISTINCT uc.id) AS usersC
           |WITH $coSaveCarry,
           |     size([x IN usersC WHERE x IN usersT]) AS inter,
           |     size(usersT) AS sizeT,
           |     size(usersC) AS sizeC
           |WITH $coSaveCarry, inter, sizeT, sizeC,
           |     CASE WHEN sizeT + sizeC - inter = 0 THEN 0.0 ELSE toFloat(inter) / toFloat(sizeT + sizeC - inter) END AS coSaveScore
           |""".stripMargin + coSaveMin
            .map(min => s"\nWHERE coSaveScore >= $min")
            .getOrElse("")

      val tagCarryBase =
        if (coSaveActiveRecipe && ingredientActiveRecipe)
          s"$nodeVar, target, ingredientScore, coSaveScore"
        else if (ingredientActiveRecipe) s"$nodeVar, target, ingredientScore"
        else if (coSaveActiveRecipe) s"$nodeVar, target, coSaveScore"
        else s"$nodeVar, target"
      val tagPart =
        if (!tagActiveRecipe) ""
        else
          s"""
           |WITH $tagCarryBase
           |OPTIONAL MATCH (target)-[:HAS_TAG]->(tt:Tag)
           |WITH $tagCarryBase, collect(DISTINCT tt.name) AS targetTags
           |OPTIONAL MATCH ($nodeVar)-[:HAS_TAG]->(ct:Tag)
           |WITH $tagCarryBase, targetTags, collect(DISTINCT ct.name) AS candidateTags
           |WITH $tagCarryBase, size([x IN candidateTags WHERE x IN targetTags]) AS interTags, size(targetTags) AS sizeTargetTags, size(candidateTags) AS sizeCandidateTags
           |WITH $tagCarryBase,, interTags, sizeTargetTags, sizeCandidateTags,
           |     CASE WHEN sizeTargetTags + sizeCandidateTags - interTags = 0 THEN 0.0 ELSE toFloat(interTags) / toFloat(sizeTargetTags + sizeCandidateTags - interTags) END AS tagScore
           |""".stripMargin + tagMin
            .map(min => s"\nWHERE tagScore >= $min")
            .getOrElse("")

      val anyMinApplied =
        (ingredientActiveRecipe && ingredientMin.isDefined) || (coSaveActiveRecipe && coSaveMin.isDefined) || (tagActiveRecipe && tagMin.isDefined)
      val finalWhereOrAnd = if (anyMinApplied) "AND" else "WHERE"
      val finalWhere = s"\n$finalWhereOrAnd $nodeVar.id <> '$analyzedId'\n"

      val sumExprParts = (if (ingredientActiveRecipe) Seq("ingredientScore")
                          else Seq()) ++ (if (coSaveActiveRecipe)
                                            Seq("coSaveScore")
                                          else Seq()) ++ (if (tagActiveRecipe)
                                                            Seq("tagScore")
                                                          else Seq())
      val sumExpr = sumExprParts.mkString(" + ")
      val denom = sumExprParts.size.max(1)
      val finalWith = s"WITH $nodeVar, ($sumExpr) / $denom AS score\n"

      base + start + ingredientPart + coSavePart + tagPart + finalWhere + finalWith
    } else {
      val analyzedId = filters.analyzedEntity.get
      val ingredientMin = filters.ingredientSimilarity.map(_.minScore)
      val coSaveMin = filters.coSaveSimilarity.map(_.minScore)
      val tagMin = filters.tagSimilarity.map(_.minScore)

      val start = s"""
         |WITH $nodeVar
         |MATCH (target:User {id: '$analyzedId'})
         |""".stripMargin

      val ingredientPart =
        if (!ingredientActiveUser) ""
        else
          s"""
             |OPTIONAL MATCH (ts:Recipe)-[:SAVED_BY]->(target)
             |OPTIONAL MATCH (tc:Recipe)-[:CREATED_BY]->(target)
             |OPTIONAL MATCH (cs:Recipe)-[:SAVED_BY]->($nodeVar)
             |OPTIONAL MATCH (cc:Recipe)-[:CREATED_BY]->($nodeVar)
             |WITH $nodeVar, target,
             |     collect(DISTINCT ts) + collect(DISTINCT tc) AS tAll,
             |     collect(DISTINCT cs) + collect(DISTINCT cc) AS cAll
             |WITH $nodeVar, target, tAll, cAll,
             |     CASE WHEN size(tAll) = 0 THEN [null] ELSE tAll END AS tAllNZ,
             |     CASE WHEN size(cAll) = 0 THEN [null] ELSE cAll END AS cAllNZ
             |UNWIND tAllNZ AS tRec
             |OPTIONAL MATCH (tRec)-[tr:HAS_INGREDIENT]->(ti:Ingredient)
             |WITH $nodeVar, target, ti.id AS ingrIdT, sum(coalesce(tr.normalizedWeight, 0.0)) AS weightT, cAllNZ
             |WITH $nodeVar, target, collect({ingredientId: ingrIdT, weight: weightT}) AS targetVector, cAllNZ
             |UNWIND cAllNZ AS cRec
             |OPTIONAL MATCH (cRec)-[ci:HAS_INGREDIENT]->(ciIngr:Ingredient)
             |WITH $nodeVar, target, targetVector, ciIngr.id AS ingrIdC, sum(coalesce(ci.normalizedWeight, 0.0)) AS weightC
             |WITH $nodeVar, target, targetVector, collect({ingredientId: ingrIdC, weight: weightC}) AS candidateVector
             |WITH $nodeVar, target, targetVector, candidateVector,
             |     reduce(dot = 0.0, x IN targetVector | dot + coalesce([y IN candidateVector WHERE y.ingredientId = x.ingredientId][0].weight, 0.0) * x.weight) AS dotProd,
             |     sqrt(reduce(s = 0.0, x IN targetVector | s + x.weight * x.weight)) AS normTarget,
             |     sqrt(reduce(s = 0.0, x IN candidateVector | s + x.weight * x.weight)) AS normCandidate
             |WITH $nodeVar, target, CASE WHEN normTarget = 0 OR normCandidate = 0 THEN 0.0 ELSE dotProd / (normTarget * normCandidate) END AS ingredientScore
             |""".stripMargin + ingredientMin
            .map(min => s"\nWHERE ingredientScore >= $min")
            .getOrElse("")

      val coSaveCarry =
        if (ingredientActiveUser) s"$nodeVar, target, ingredientScore"
        else s"$nodeVar, target"
      val coSavePart =
        if (!coSaveActiveUser) ""
        else
          s"""
           |WITH $coSaveCarry
           |OPTIONAL MATCH (target)<-[:SAVED_BY]-(tr:Recipe)
           |OPTIONAL MATCH (target)<-[:CREATED_BY]-(tcr:Recipe)
           |WITH $coSaveCarry, (collect(DISTINCT tr.id) + collect(DISTINCT tcr.id)) AS recipesT
           |OPTIONAL MATCH ($nodeVar)<-[:SAVED_BY]-(cr:Recipe)
           |OPTIONAL MATCH ($nodeVar)<-[:CREATED_BY]-(ccr:Recipe)
           |WITH $coSaveCarry, recipesT, (collect(DISTINCT cr.id) + collect(DISTINCT ccr.id)) AS recipesC
           |WITH $coSaveCarry,
           |     size([x IN recipesC WHERE x IN recipesT]) AS inter,
           |     size(recipesT) AS sizeT,
           |     size(recipesC) AS sizeC
           |WITH $coSaveCarry, inter, sizeT, sizeC,
           |     CASE WHEN sizeT + sizeC - inter = 0 THEN 0.0 ELSE toFloat(inter) / toFloat(sizeT + sizeC - inter) END AS coSaveScore
           |""".stripMargin + coSaveMin
            .map(min => s"\nWHERE coSaveScore >= $min")
            .getOrElse("")

      val tagCarryBase =
        if (coSaveActiveUser && ingredientActiveUser)
          s"$nodeVar, target, ingredientScore, coSaveScore"
        else if (ingredientActiveUser) s"$nodeVar, target, ingredientScore"
        else if (coSaveActiveUser) s"$nodeVar, target, coSaveScore"
        else s"$nodeVar, target"
      val tagPart =
        if (!tagActiveUser) ""
        else
          s"""
             |WITH $tagCarryBase
             |OPTIONAL MATCH (tss:Recipe)-[:SAVED_BY]->(target)
             |OPTIONAL MATCH (tcc:Recipe)-[:CREATED_BY]->(target)
             |OPTIONAL MATCH (css:Recipe)-[:SAVED_BY]->($nodeVar)
             |OPTIONAL MATCH (ccc:Recipe)-[:CREATED_BY]->($nodeVar)
             |WITH $tagCarryBase,
             |     collect(DISTINCT tss) + collect(DISTINCT tcc) AS tAllTags,
             |     collect(DISTINCT css) + collect(DISTINCT ccc) AS cAllTags
             |WITH $tagCarryBase, tAllTags, cAllTags,
             |     CASE WHEN size(tAllTags) = 0 THEN [null] ELSE tAllTags END AS tAllTagsNZ,
             |     CASE WHEN size(cAllTags) = 0 THEN [null] ELSE cAllTags END AS cAllTagsNZ
             |UNWIND tAllTagsNZ AS tTagRec
             |OPTIONAL MATCH (tTagRec)-[:HAS_TAG]->(tt:Tag)
             |WITH $tagCarryBase, cAllTagsNZ, collect(DISTINCT tt.name) AS targetTags
             |UNWIND cAllTagsNZ AS cTagRec
             |OPTIONAL MATCH (cTagRec)-[:HAS_TAG]->(ct:Tag)
             |WITH $tagCarryBase, targetTags, collect(DISTINCT ct.name) AS candidateTags
             |WITH $tagCarryBase,
             |     size([x IN candidateTags WHERE x IN targetTags]) AS interTags,
             |     size(targetTags) AS sizeTargetTags,
             |     size(candidateTags) AS sizeCandidateTags
             |WITH $tagCarryBase, interTags, sizeTargetTags, sizeCandidateTags,
             |     CASE WHEN sizeTargetTags + sizeCandidateTags - interTags = 0 THEN 0.0 ELSE toFloat(interTags) / toFloat(sizeTargetTags + sizeCandidateTags - interTags) END AS tagScore
             |""".stripMargin + tagMin
            .map(min => s"\nWHERE tagScore >= $min")
            .getOrElse("")

      val anyMinApplied =
        (ingredientActiveUser && ingredientMin.isDefined) || (coSaveActiveUser && coSaveMin.isDefined) || (tagActiveUser && tagMin.isDefined)
      val finalWhereOrAnd = if (anyMinApplied) "AND" else "WHERE"
      val finalWhere = s"\n$finalWhereOrAnd $nodeVar.id <> '$analyzedId'\n"

      val sumExprParts = (if (ingredientActiveUser) Seq("ingredientScore")
                          else Seq()) ++ (if (coSaveActiveUser)
                                            Seq("coSaveScore")
                                          else Seq()) ++ (if (tagActiveUser)
                                                            Seq("tagScore")
                                                          else Seq())
      val sumExpr = sumExprParts.mkString(" + ")
      val denom = sumExprParts.size.max(1)
      val finalWith = s"WITH $nodeVar, ($sumExpr) / $denom AS score\n"

      base + start + ingredientPart + coSavePart + tagPart + finalWhere + finalWith
    }
  }
}
