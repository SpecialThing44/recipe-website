package api.recipes
import api.{Persisting, Querying}
import com.google.inject.ImplementedBy
import domain.food.recipes.Recipe

@ImplementedBy(classOf[RecipeFacade])
trait RecipeApi extends Persisting[Recipe] with Querying[Recipe]
