package persistence.filters

import domain.filters.Filters
import io.circe.syntax.EncoderOps
import persistence.users.UserConverter.lowerPrefix

object FiltersConverter {
  def similarityActive(filters: Filters): Boolean =
    (filters.ingredientSimilarity.isDefined || filters.coSaveSimilarity.isDefined || filters.tagSimilarity.isDefined) && filters.analyzedEntity.isDefined
  def getOrderLine(filters: Filters, nodeVar: String): String = {
    if (similarityActive(filters)) "ORDER BY score DESC" else ""
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
    val vegetarianClause =
      filters.vegetarian.map(vegetarian =>
        s"$nodeVar.vegetarian = '$vegetarian'"
      )
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
          s"MATCH ($nodeVar)-[:HAS_INGREDIENT]->(hasIngredient:Ingredient {name: '$ingredient'})"
        )
        .mkString("\n")
    )
    val notIngredientsClause = filters.notIngredients.map(notIngredients =>
      notIngredients
        .map(notIngredient =>
          s"MATCH ($nodeVar) WHERE NOT ($nodeVar)-[:HAS_INGREDIENT]->(notIngredient:Ingredient {name: '$notIngredient'})"
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
      vegetarianClause,
      veganClause,
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
    val ingredientActive = filters.ingredientSimilarity.isDefined && filters.analyzedEntity.isDefined && isRecipeNode
    val coSaveActive = filters.coSaveSimilarity.isDefined && filters.analyzedEntity.isDefined && isRecipeNode
    val tagActive = filters.tagSimilarity.isDefined && filters.analyzedEntity.isDefined && isRecipeNode

    if (!ingredientActive && !coSaveActive && !tagActive) base
    else {
      val analyzedId = filters.analyzedEntity.get
      val ingredientMin = filters.ingredientSimilarity.map(_.minScore)
      val coSaveMin = filters.coSaveSimilarity.map(_.minScore)
      val tagMin = filters.tagSimilarity.map(_.minScore)

      val start = s"""
         |WITH $nodeVar
         |MATCH (target:Recipe {id: '$analyzedId'})
         |""".stripMargin

      val ingredientPart =
        if (!ingredientActive) ""
        else s"""
           |MATCH (target)-[tr:HAS_INGREDIENT]->(ti:Ingredient)
           |WITH $nodeVar, target, collect({ingredientId: ti.id, weight: coalesce(tr.normalizedWeight, 0.0)}) AS targetVec
           |MATCH ($nodeVar)-[ci:HAS_INGREDIENT]->(ciIngr:Ingredient)
           |WITH $nodeVar, target, targetVec, collect({ingredientId: ciIngr.id, weight: coalesce(ci.normalizedWeight, 0.0)}) AS candVec
           |WITH $nodeVar, target, targetVec, candVec,
           |     reduce(dot = 0.0, x IN targetVec | dot + coalesce([y IN candVec WHERE y.ingredientId = x.ingredientId][0].weight, 0.0) * x.weight) AS dotProd,
           |     sqrt(reduce(s = 0.0, x IN targetVec | s + x.weight * x.weight)) AS normT,
           |     sqrt(reduce(s = 0.0, x IN candVec | s + x.weight * x.weight)) AS normC
           |WITH $nodeVar, target, CASE WHEN normT = 0 OR normC = 0 THEN 0.0 ELSE dotProd / (normT * normC) END AS ingredientScore
           |""".stripMargin + ingredientMin.map(min => s"\nWHERE ingredientScore >= $min").getOrElse("")

      val coSaveCarry = if (ingredientActive) s"$nodeVar, target, ingredientScore" else s"$nodeVar, target"
      val coSavePart =
        if (!coSaveActive) ""
        else s"""
           |WITH $coSaveCarry
           |OPTIONAL MATCH (target)-[:SAVED_BY]->(ur:User)
           |WITH $coSaveCarry, collect(DISTINCT ur.id) AS usersT
           |OPTIONAL MATCH ($nodeVar)-[:SAVED_BY]->(uc:User)
           |WITH $coSaveCarry, usersT, collect(DISTINCT uc.id) AS usersC
           |WITH $coSaveCarry,
           |     size([x IN usersC WHERE x IN usersT]) AS inter,
           |     size(usersT) AS sizeT,
           |     size(usersC) AS sizeC,
           |     CASE WHEN sizeT + sizeC - inter = 0 THEN 0.0 ELSE toFloat(inter) / toFloat(sizeT + sizeC - inter) END AS coSaveScore
           |""".stripMargin + coSaveMin.map(min => s"\nWHERE coSaveScore >= $min").getOrElse("")

      val tagCarryBase = if (coSaveActive && ingredientActive) s"$nodeVar, target, ingredientScore, coSaveScore" else if (ingredientActive) s"$nodeVar, target, ingredientScore" else if (coSaveActive) s"$nodeVar, target, coSaveScore" else s"$nodeVar, target"
      val tagPart =
        if (!tagActive) ""
        else s"""
           |WITH $tagCarryBase
           |OPTIONAL MATCH (target)-[:HAS_TAG]->(tt:Tag)
           |WITH $tagCarryBase, collect(DISTINCT tt.name) AS targetTags
           |OPTIONAL MATCH ($nodeVar)-[:HAS_TAG]->(ct:Tag)
           |WITH $tagCarryBase, targetTags, collect(DISTINCT ct.name) AS candidateTags
           |WITH $tagCarryBase,
           |     size([x IN candidateTags WHERE x IN targetTags]) AS interTags,
           |     size(targetTags) AS sizeTT,
           |     size(candidateTags) AS sizeCT,
           |     CASE WHEN sizeTT + sizeCT - interTags = 0 THEN 0.0 ELSE toFloat(interTags) / toFloat(sizeTT + sizeCT - interTags) END AS tagScore
           |""".stripMargin + tagMin.map(min => s"\nWHERE tagScore >= $min").getOrElse("")

      val anyMinApplied = (ingredientActive && ingredientMin.isDefined) || (coSaveActive && coSaveMin.isDefined) || (tagActive && tagMin.isDefined)
      val finalWhereOrAnd = if (anyMinApplied) "AND" else "WHERE"
      val finalWhere = s"\n$finalWhereOrAnd $nodeVar.id <> '$analyzedId'\n"

      val sumExprParts = (if (ingredientActive) Seq("ingredientScore") else Seq()) ++ (if (coSaveActive) Seq("coSaveScore") else Seq()) ++ (if (tagActive) Seq("tagScore") else Seq())
      val sumExpr = sumExprParts.mkString(" + ")
      val denom = sumExprParts.size.max(1)
      val finalWith = s"WITH $nodeVar, ($sumExpr) / $denom AS score\n"

      base + start + ingredientPart + coSavePart + tagPart + finalWhere + finalWith
    }
  }
}
