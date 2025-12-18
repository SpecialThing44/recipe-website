package integration

import domain.filters.{Filters, StringFilter}
import domain.ingredients.{Ingredient, IngredientInput, IngredientUpdateInput}
import domain.users.{User, UserInput}
import zio.{Runtime, Unsafe, ZLayer}

import java.util.UUID
import scala.language.{implicitConversions, strictEquality}

class IngredientsIntegrationTest extends IntegrationTestFramework {
  def ingredientsMatch(
      ingredient1: Ingredient,
      ingredient2: Ingredient
  ): Unit = {
    ingredient1.id shouldBe ingredient2.id
    ingredient1.name shouldBe ingredient2.name
    ingredient1.aliases shouldBe ingredient2.aliases
    ingredient1.wikiLink shouldBe ingredient2.wikiLink
    ingredient1.tags.toSet shouldBe ingredient2.tags.toSet
    ingredient1.createdBy.id shouldBe ingredient2.createdBy.id
  }

  def ingredientInputsMatch(
      ingredientInput: IngredientInput,
      ingredient: Ingredient
  ): Unit = {
    ingredient.name shouldBe ingredientInput.name
    ingredient.aliases shouldBe ingredientInput.aliases
    ingredient.wikiLink shouldBe ingredientInput.wikiLink.toLowerCase
    ingredient.tags.toSet shouldBe ingredientInput.tags.toSet
  }

  def ingredientUpdatesMatch(
      ingredientUpdateInput: IngredientUpdateInput,
      ingredient: Ingredient
  ): Unit = {
    ingredientUpdateInput.name.map(name => ingredient.name shouldBe name)
    ingredientUpdateInput.aliases.map(aliases =>
      ingredient.aliases shouldBe aliases
    )
    ingredientUpdateInput.wikiLink.map(wikiLink =>
      ingredient.wikiLink shouldBe wikiLink.toLowerCase
    )
    ingredientUpdateInput.tags.map(tags =>
      ingredient.tags.toSet shouldBe tags.toSet
    )
  }
  val standardIngredientInput = IngredientInput(
    name = "Test Ingredient",
    aliases = Seq("test", "ingredient"),
    wikiLink = "https://en.wikipedia.org/wiki/Ingredient",
    tags = Seq("test", "sample")
  )

  val tomatoIngredientInput = IngredientInput(
    name = "Tomato",
    aliases = Seq("tomato", "tomatoes"),
    wikiLink = "https://en.wikipedia.org/wiki/Tomato",
    tags = Seq("vegetable", "fruit")
  )

  val onionIngredientInput = IngredientInput(
    name = "Onion",
    aliases = Seq("onion", "onions"),
    wikiLink = "https://en.wikipedia.org/wiki/Onion",
    tags = Seq("vegetable")
  )

  val chickenIngredientInput = IngredientInput(
    name = "Chicken",
    aliases = Seq("chicken", "poultry"),
    wikiLink = "https://en.wikipedia.org/wiki/Chicken",
    tags = Seq("meat", "protein")
  )

  var user: User = _

  override def beforeEach(): Unit = {
    super.beforeEach()
    user = createTestAdminUser(standardUserInput)
    login(user.id)
  }

  it should "create an ingredient and get it by ID" in {
    val ingredient = createTestIngredient(standardIngredientInput)
    ingredientInputsMatch(standardIngredientInput, ingredient)
    ingredient.createdBy.id shouldBe user.id

    val retrievedIngredient = getIngredientById(ingredient.id)
    ingredientsMatch(ingredient, retrievedIngredient)
  }

  it should "update an ingredient" in {
    val ingredient = createTestIngredient(standardIngredientInput)

    val updateInput = IngredientUpdateInput(
      name = Some("Updated Ingredient"),
      aliases = Some(Seq("updated", "test", "ingredient")),
      wikiLink = Some("https://en.wikipedia.org/wiki/Updated_Ingredient"),
      tags = Some(Seq("updated", "test", "sample"))
    )

    val updatedIngredient = updateIngredient(ingredient, updateInput)
    updatedIngredient.id shouldBe ingredient.id
    ingredientUpdatesMatch(updateInput, updatedIngredient)

    val retrievedIngredient = getIngredientById(ingredient.id)
    ingredientsMatch(updatedIngredient, retrievedIngredient)
  }

  it should "fetch ingredients with filters" in {
    val ingredient1 = createTestIngredient(tomatoIngredientInput)
    val ingredient2 = createTestIngredient(onionIngredientInput)
    val ingredient3 = createTestIngredient(chickenIngredientInput)

    val idFilter = Filters.empty().copy(id = Some(ingredient1.id))
    val idFilterResults = listIngredients(idFilter)
    idFilterResults.length shouldBe 1
    idFilterResults.head.id shouldBe ingredient1.id

    val idsFilter =
      Filters.empty().copy(ids = Some(List(ingredient1.id, ingredient2.id)))
    val idsFilterResults = listIngredients(idsFilter)
    idsFilterResults.length shouldBe 2
    idsFilterResults.map(_.id) should contain allOf (
      ingredient1.id,
      ingredient2.id
    )

    val nameFilter = Filters
      .empty()
      .copy(
        name = Some(
          StringFilter(
            equals = Some(tomatoIngredientInput.name.toLowerCase),
            None,
            None,
            None,
            None
          )
        )
      )
    val nameFilterResults = listIngredients(nameFilter)
    nameFilterResults.length shouldBe 1
    nameFilterResults.head.name shouldBe "Tomato"

    val nameContainsFilter = Filters
      .empty()
      .copy(
        name = Some(StringFilter(None, None, contains = Some("on"), None, None))
      )
    val nameContainsResults = listIngredients(nameContainsFilter)
    nameContainsResults.length shouldBe 1
    nameContainsResults.head.name shouldBe "Onion"
  }

  it should "delete an ingredient" in {
    val ingredient = createTestIngredient(standardIngredientInput)

    val retrievedIngredient = getIngredientById(ingredient.id)
    ingredientsMatch(ingredient, retrievedIngredient)

    val deletedIngredient = deleteIngredient(ingredient.id)
    deletedIngredient.id shouldBe ingredient.id
  }
}
