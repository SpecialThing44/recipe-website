package api.ingredients

import api.{Persisting, Querying}
import com.google.inject.ImplementedBy
import domain.ingredients.Ingredient

@ImplementedBy(classOf[IngredientsFacade])
trait IngredientsApi
    extends Persisting[Ingredient, Ingredient, Ingredient]
    with Querying[Ingredient]
