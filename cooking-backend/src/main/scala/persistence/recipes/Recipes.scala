package persistence.recipes

import api.Querying
import com.google.inject.ImplementedBy
import domain.food.recipes.Recipe
import persistence.DbPersisting

@ImplementedBy(classOf[RecipePersistence])
trait Recipes extends DbPersisting[Recipe] with Querying[Recipe]
