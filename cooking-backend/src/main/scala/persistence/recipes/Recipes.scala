package persistence.recipes

import api.{Persisting, Querying}
import com.google.inject.ImplementedBy
import domain.food.recipes.Recipe

@ImplementedBy(classOf[RecipePersistence])
trait Recipes extends Persisting[Recipe] with Querying[Recipe]
