package persistence.ingredients

import api.Querying
import com.google.inject.ImplementedBy
import domain.ingredients.Ingredient
import persistence.DbPersisting

@ImplementedBy(classOf[IngredientsPersistence])
trait Ingredients extends DbPersisting[Ingredient] with Querying[Ingredient]
