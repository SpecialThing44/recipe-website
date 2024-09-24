package api.ingredients

import api.Querying
import domain.food.ingredients.Ingredient
import persistence.Persisting

trait IngredientsApi extends Persisting[Ingredient] with Querying[Ingredient]
