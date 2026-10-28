package persistence.filters

import domain.filters.{Filters, NumberFilter, SimilarityFilter, StringFilter}
import integration.{IntegrationTestFramework, TestAppHolder}
import persistence.neo4j.Database
import zio.{Runtime, Unsafe}

import java.util.UUID

class QueryCombinationsIntegrationTest extends IntegrationTestFramework {
  it should "compile combinations of entity, relationship, property, and similarity filters" in {
    val database = TestAppHolder.application.injector.instanceOf[Database]
    val userId = UUID.randomUUID()
    val recipeId = UUID.randomUUID()
    val name = StringFilter.empty().copy(contains = Some("soup"))
    val baseFilters = Seq(
      Filters.empty(),
      Filters
        .empty()
        .copy(savedByUser = Some(userId), ids = Some(List(recipeId))),
      Filters.empty().copy(belongsToUser = Some(userId), name = Some(name)),
      Filters
        .empty()
        .copy(tags = Some(Seq("quick")), ids = Some(List(recipeId))),
      Filters
        .empty()
        .copy(ingredients = Some(Seq("tomato")), name = Some(name)),
      Filters
        .empty()
        .copy(
          notIngredients = Some(Seq("onion")),
          cookTime = Some(NumberFilter(Some(1), Some(30)))
        )
    )
    val similarity = SimilarityFilter(1.0, 1.0, 1.0, 0.0)

    for {
      (nodeVar, label) <- Seq(
        "recipe" -> "Recipe",
        "user" -> "User",
        "ingredient" -> "Ingredient"
      )
      base <- baseFilters
      target <- 0 to 2
      metrics <- 0 to 7
    } {
      val filters = base.copy(
        analyzedRecipe = Option.when(target == 1)(recipeId),
        analyzedUser = Option.when(target == 2)(userId),
        ingredientSimilarity = Option.when((metrics & 1) != 0)(similarity),
        coSaveSimilarity = Option.when((metrics & 2) != 0)(similarity),
        tagSimilarity = Option.when((metrics & 4) != 0)(similarity)
      )
      val fragment = FiltersConverter.toCypher(filters, nodeVar)
      val cypher = s"""
        |EXPLAIN MATCH ($nodeVar:$label)
        |${fragment.cypher}
        |${CypherFragment.getWithScoreLine(filters, s"WITH $nodeVar", nodeVar)}
        |${CypherFragment.getOrderLine(filters, nodeVar)}
        |RETURN $nodeVar
        |""".stripMargin

      withClue(
        s"entity=$nodeVar target=$target metrics=$metrics filters=$base\n$cypher"
      ) {
        Unsafe.unsafe { implicit unsafe =>
          Runtime.default.unsafe
            .run(
              database.readTransaction(cypher, fragment.params, _.consume())
            )
            .getOrThrow()
        }
      }
    }
  }
}
