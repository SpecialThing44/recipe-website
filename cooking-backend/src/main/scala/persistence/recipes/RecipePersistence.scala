package persistence.recipes

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.ingredients.Unit
import domain.recipes.Recipe
import persistence.cypher.*
import persistence.filters.{CypherFragment, FiltersConverter}
import persistence.neo4j.Database
import zio.ZIO

import java.util.UUID
import scala.jdk.CollectionConverters.*

class RecipePersistence @Inject() (database: Database) extends Recipes {
  private implicit val graph: RecipeGraph = RecipeGraph()

  private def mergeFragments(fragments: Seq[CypherFragment]): CypherFragment =
    CypherFragment(
      fragments.map(_.cypher).mkString("\n"),
      fragments.foldLeft(Map.empty[String, AnyRef])((acc, fragment) =>
        acc ++ fragment.params
      )
    )

  private def createTagStatementsFor(
      tags: Seq[String],
      includeWithUser: Boolean
  ): CypherFragment = {
    val withLine =
      if (includeWithUser) s"\n${WithStatement.apply}, user\n" else "\n"
    val fragments = tags.zipWithIndex.map { case (tag, index) =>
      val alias = s"tag$index"
      val tagNameParam = s"recipe_tag_name_$index"
      val tagLowerNameParam = s"recipe_tag_lower_name_$index"
      CypherFragment(
        s"""
           |MERGE ($alias:${graph.tagLabel} {name: $$${tagNameParam}, lowername: $$${tagLowerNameParam}})
           |CREATE (${graph.nodeVar})-[:${graph.tagRelation}]->($alias)
           |$withLine""".stripMargin,
        Map(tagNameParam -> tag, tagLowerNameParam -> tag.toLowerCase)
      )
    }
    mergeFragments(fragments)
  }

  private def createIngredientStatementsFor(
      entity: Recipe,
      includeWithUser: Boolean
  ): CypherFragment = {
    val withLine =
      if (includeWithUser) s"\n${WithStatement.apply}, user\n" else "\n"
    val standardizedWeights = entity.ingredients.map(ii =>
      Unit.toStandardizedAmount(ii.quantity.unit, ii.quantity.amount)
    )
    val totalStandardized = standardizedWeights.map(_.toDouble).sum
    val fragments = entity.ingredients
      .zip(standardizedWeights)
      .zipWithIndex
      .map { case ((ingredientData, standardizedWeight), index) =>
        val normalizedWeight =
          if (totalStandardized == 0) 0.0
          else standardizedWeight.toDouble / totalStandardized
        val ingredientAlias = s"ing$index"
        val ingredientIdParam = s"recipe_ingredient_id_$index"
        val amountParam = s"recipe_ingredient_amount_$index"
        val unitParam = s"recipe_ingredient_unit_$index"
        val weightParam = s"recipe_ingredient_weight_$index"
        val rawNormalizedParam = s"recipe_ingredient_raw_normalized_$index"
        val normalizedParam = s"recipe_ingredient_normalized_$index"
        val descriptionParam = s"recipe_ingredient_description_$index"
        CypherFragment(
          s"""
             |MATCH ($ingredientAlias:Ingredient {id: $$${ingredientIdParam}})
             |CREATE (${graph.nodeVar})-[:HAS_INGREDIENT {amount: $$${amountParam}, unit: $$${unitParam}, weight: $$${weightParam}, rawNormalizedWeight: $$${rawNormalizedParam}, normalizedWeight: $$${normalizedParam}, description: $$${descriptionParam}}]->($ingredientAlias)
             |$withLine""".stripMargin,
          Map(
            ingredientIdParam -> ingredientData.ingredient.id.toString,
            amountParam -> Double.box(ingredientData.quantity.amount),
            unitParam -> ingredientData.quantity.unit.name,
            weightParam -> Double.box(standardizedWeight),
            rawNormalizedParam -> Double.box(normalizedWeight),
            normalizedParam -> Double.box(normalizedWeight),
            descriptionParam -> ingredientData.description.getOrElse("")
          )
        )
      }
    mergeFragments(fragments)
  }

  override def list(query: Filters): ZIO[ApiContext, Throwable, Seq[Recipe]] = {
    val withLine =
      s"WITH ${graph.nodeVar}, user, collect(DISTINCT ${graph.tagVar}.name) as tags, collect(DISTINCT {ingredient: properties(ingredient), amount: ri.amount, unit: ri.unit, weight: ri.weight, rawNormalizedWeight: coalesce(ri.rawNormalizedWeight, ri.normalizedWeight, 0.0), normalizedWeight: coalesce(ri.normalizedWeight, ri.rawNormalizedWeight, 0.0), description: ri.description}) as ingredientQuantities"
    val orderLine = CypherFragment.getOrderLine(query, graph.nodeVar)
    val filterCypher = FiltersConverter.toCypher(query, graph.nodeVar)
    val pagingCypher = CypherFragment.limitAndSkipStatement(query)
    database.readTransaction(
      s"""
         |${MatchStatement.apply}
         |${filterCypher.cypher}
         |${MatchRelationship.outgoing("CREATED_BY", "user", "User")}
         |OPTIONAL MATCH (${graph.nodeVar})-[ri:HAS_INGREDIENT]->(ingredient:Ingredient)
         |OPTIONAL ${MatchRelationship.outgoing(
          graph.tagRelation,
          "tag",
          graph.tagLabel
        )}
         |${CypherFragment.getWithScoreLine(query, withLine)}
         |$orderLine
         |${pagingCypher.cypher}
         |${ReturnStatement.apply}, user as createdBy, tags, ingredientQuantities
         |""".stripMargin,
      filterCypher.params ++ pagingCypher.params,
      (result: org.neo4j.driver.Result) =>
        result.asScala
          .map(record => attachAllToRecord(record))
          .toSeq
    )
  }

  override def create(entity: Recipe): ZIO[ApiContext, Throwable, Recipe] = {
    val recipeProperties = RecipeConverter.toGraph(entity).asJava
    val createTagStatements = createTagStatementsFor(
      entity.tags,
      includeWithUser = true,
    )
    val createIngredientStatements =
      createIngredientStatementsFor(entity, includeWithUser = true)
    val params =
      Map(
        "recipeProperties" -> recipeProperties,
        "createdById" -> entity.createdBy.id.toString
      ) ++ createTagStatements.params ++ createIngredientStatements.params

    database.writeTransaction(
      s"""
         |CREATE (${graph.nodeVar}:${graph.nodeLabel})
         |SET ${graph.nodeVar} = $$recipeProperties
         |${WithStatement.apply}
         |MATCH (user:User {id: $$createdById})
         |CREATE (${graph.nodeVar})-[:CREATED_BY]->(user)
         |${WithStatement.apply}, user
         |${createTagStatements.cypher}
         |${createIngredientStatements.cypher}
         |OPTIONAL MATCH (${graph.nodeVar})-[ri:HAS_INGREDIENT]->(ingredient:Ingredient)
         |OPTIONAL ${MatchRelationship.outgoing(
          graph.tagRelation,
          "tag",
          graph.tagLabel
        )}
         |WITH ${graph.nodeVar}, user, collect(DISTINCT ${graph.tagVar}.name) as tags, collect(DISTINCT {ingredient: properties(ingredient), amount: ri.amount, unit: ri.unit, weight: ri.weight, rawNormalizedWeight: coalesce(ri.rawNormalizedWeight, ri.normalizedWeight, 0.0), normalizedWeight: coalesce(ri.normalizedWeight, ri.rawNormalizedWeight, 0.0), description: ri.description}) as ingredientQuantities
         |${ReturnStatement.apply}, user as createdBy, tags, ingredientQuantities
         |""".stripMargin,
      params,
      (result: org.neo4j.driver.Result) => {
        if (result.hasNext) {
          attachAllToRecord(result.next())
        } else {
          throw domain.types.NoSuchEntityError(
            s"Create for ${graph.nodeLabel} has failed for some reason"
          )
        }
      }
    )
  }

  override def update(
      entity: Recipe,
      originalEntity: Recipe
  ): ZIO[ApiContext, Throwable, Recipe] = {
    val recipeProperties = RecipeConverter.toGraph(entity).asJava
    val createTagStatements = createTagStatementsFor(
      entity.tags,
      includeWithUser = true,
    )
    val createIngredientStatements =
      createIngredientStatementsFor(entity, includeWithUser = true)
    val params =
      Map(
        "recipeId" -> entity.id.toString,
        "recipeProperties" -> recipeProperties
      ) ++ createTagStatements.params ++ createIngredientStatements.params

    database.writeTransaction(
      s"""
         |MATCH (${graph.nodeVar}:${graph.nodeLabel} {id: $$recipeId})
         |SET ${graph.nodeVar} = $$recipeProperties
         |${WithStatement.apply}
         |OPTIONAL MATCH (${graph.nodeVar})-[r:${graph.tagRelation}]->()
         |DELETE r
         |WITH DISTINCT ${graph.nodeVar}
         |OPTIONAL MATCH (${graph.nodeVar})-[ri:HAS_INGREDIENT]->()
         |DELETE ri
         |WITH DISTINCT ${graph.nodeVar}
         |${MatchRelationship.outgoing("CREATED_BY", "user", "User")}
         |${WithStatement.apply}, user
         |${createTagStatements.cypher}
         |${createIngredientStatements.cypher}
         |${WithStatement.apply}, user
         |OPTIONAL MATCH (${graph.nodeVar})-[ri:HAS_INGREDIENT]->(ingredient:Ingredient)
         |OPTIONAL ${MatchRelationship.outgoing(
          graph.tagRelation,
          graph.tagVar,
          graph.tagLabel
        )}
         |WITH ${graph.nodeVar}, user, collect(DISTINCT ${graph.tagVar}.name) as tags, collect(DISTINCT {ingredient: properties(ingredient), amount: ri.amount, unit: ri.unit, weight: ri.weight, rawNormalizedWeight: coalesce(ri.rawNormalizedWeight, ri.normalizedWeight, 0.0), normalizedWeight: coalesce(ri.normalizedWeight, ri.rawNormalizedWeight, 0.0), description: ri.description}) as ingredientQuantities
         |${ReturnStatement.apply}, user as createdBy, tags, ingredientQuantities
         |""".stripMargin,
      params,
      (result: org.neo4j.driver.Result) => {
        if (result.hasNext) {
          attachAllToRecord(result.next())
        } else {
          throw domain.types.NoSuchEntityError(
            s"Update for ${graph.nodeLabel} with id ${entity.id} has failed for some reason"
          )
        }
      }
    )
  }

  override def delete(id: UUID): ZIO[ApiContext, Throwable, Recipe] =
    for {
      recipe <- getById(id)
      _ <- database.writeTransaction(
        s"""
           |MATCH (${graph.nodeVar}:${graph.nodeLabel} {id: $$recipeId})
           |${DeleteStatement.apply}
           |""".stripMargin,
        Map("recipeId" -> id.toString),
        (_: org.neo4j.driver.Result) => ()
      )
    } yield recipe

  override def getById(id: UUID): ZIO[ApiContext, Throwable, Recipe] =
    database.readTransaction(
      s"""
         |MATCH (${graph.nodeVar}:${graph.nodeLabel} {id: $$recipeId})
         |${MatchRelationship.outgoing("CREATED_BY", "user", "User")}
         |OPTIONAL MATCH (${graph.nodeVar})-[ri:HAS_INGREDIENT]->(ingredient:Ingredient)
         |OPTIONAL ${MatchRelationship.outgoing(
          graph.tagRelation,
          graph.tagVar,
          graph.tagLabel
        )}
         |WITH ${graph.nodeVar}, user, collect(DISTINCT ${graph.tagVar}.name) as tags, collect(DISTINCT {ingredient: properties(ingredient), amount: ri.amount, unit: ri.unit, weight: ri.weight, rawNormalizedWeight: coalesce(ri.rawNormalizedWeight, ri.normalizedWeight, 0.0), normalizedWeight: coalesce(ri.normalizedWeight, ri.rawNormalizedWeight, 0.0), description: ri.description}) as ingredientQuantities
         |${ReturnStatement.apply}, user as createdBy, tags, ingredientQuantities
         |""".stripMargin,
      Map("recipeId" -> id.toString),
      (result: org.neo4j.driver.Result) => {
        if (result.hasNext) {
          attachAllToRecord(result.next())
        } else {
          throw domain.types.NoSuchEntityError(
            s"${graph.nodeLabel} with id $id not found"
          )
        }
      }
    )

  override def save(
      recipeId: UUID,
      userId: UUID
  ): ZIO[ApiContext, Throwable, Recipe] =
    database.writeTransaction(
      s"""
         |MATCH (${graph.nodeVar}:${graph.nodeLabel} {id: $$recipeId})
         |MATCH (user:User {id: $$userId})
         |MERGE (${graph.nodeVar})-[:SAVED_BY]->(user)
         |${WithStatement.apply}
         |${MatchRelationship.outgoing("CREATED_BY", "created", "User")}
         |WITH ${graph.nodeVar}, created as user
         |OPTIONAL MATCH (${graph.nodeVar})-[ri:HAS_INGREDIENT]->(ingredient:Ingredient)
         |OPTIONAL ${MatchRelationship.outgoing(
          graph.tagRelation,
          graph.tagVar,
          graph.tagLabel
        )}
         |WITH ${graph.nodeVar}, user, collect(DISTINCT ${graph.tagVar}.name) as tags, collect(DISTINCT {ingredient: properties(ingredient), amount: ri.amount, unit: ri.unit, weight: ri.weight, rawNormalizedWeight: coalesce(ri.rawNormalizedWeight, ri.normalizedWeight, 0.0), normalizedWeight: coalesce(ri.normalizedWeight, ri.rawNormalizedWeight, 0.0), description: ri.description}) as ingredientQuantities
         |${ReturnStatement.apply}, user as createdBy, tags, ingredientQuantities
         |""".stripMargin,
      Map("recipeId" -> recipeId.toString, "userId" -> userId.toString),
      (result: org.neo4j.driver.Result) => {
        if (result.hasNext) attachAllToRecord(result.next())
        else
          throw domain.types.NoSuchEntityError(
            s"Save failed for ${graph.nodeLabel} $recipeId"
          )
      }
    )

  private def attachAllToRecord(record: org.neo4j.driver.Record): Recipe = {
    val recipeMap =
      new java.util.HashMap[String, Object](record.get(graph.nodeVar).asMap())
    val userMap = record.get("createdBy").asMap()
    val tags = record.get("tags").asList().asScala.map(_.toString).toSeq
    val ingredientQuantities = record
      .get("ingredientQuantities")
      .asList((v: org.neo4j.driver.Value) => v.asMap())
      .asScala
      .map(entry => {
        val m = new java.util.HashMap[String, AnyRef]()
        val iter = entry.entrySet().iterator()
        while (iter.hasNext) {
          val e = iter.next()
          m.put(e.getKey, e.getValue)
        }
        m
      })
    recipeMap.put("createdBy", userMap)
    recipeMap.put("tags", tags)
    val iqList = new java.util.ArrayList[java.util.Map[String, AnyRef]]()
    var idx = 0
    while (idx < ingredientQuantities.size) {
      val map = ingredientQuantities(idx)
      iqList.add(map)
      idx = idx + 1
    }
    recipeMap.put("ingredientQuantities", iqList)
    RecipeConverter.toDomain(recipeMap)
  }

  override def deleteAll(): ZIO[ApiContext, Throwable, scala.Unit] = for {
    _ <- database.writeTransaction(
      s"""
       |${MatchStatement.apply}
       |DETACH DELETE ${graph.nodeVar}
       |""".stripMargin,
      (_: org.neo4j.driver.Result) => ()
    )
  } yield ()

  def getTotalRecipeCount(): zio.Task[Int] =
    database.readTransaction(
      """
        |MATCH (recipe:Recipe)
        |RETURN count(recipe) AS totalRecipes
        |""".stripMargin,
      (result: org.neo4j.driver.Result) =>
        if (result.hasNext) result.next().get("totalRecipes").asInt() else 0
    )
}
