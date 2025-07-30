package api.recipes
import api.{Persisting, Querying}
import com.google.inject.ImplementedBy
import domain.food.recipes.{Recipe, RecipeInput}

@ImplementedBy(classOf[RecipeFacade])
trait RecipeApi extends Persisting[Recipe, RecipeInput, RecipeInput] with Querying[Recipe]
