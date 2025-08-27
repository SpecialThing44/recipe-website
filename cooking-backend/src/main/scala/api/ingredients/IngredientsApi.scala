package api.ingredients

import api.{Persisting, Querying}
import com.google.inject.ImplementedBy
import domain.ingredients.{Ingredient, IngredientInput, IngredientUpdateInput}

@ImplementedBy(classOf[IngredientsFacade])
trait IngredientsApi
    extends Persisting[Ingredient, IngredientInput, IngredientUpdateInput]
    with Querying[Ingredient]
