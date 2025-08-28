package api.recipes
import api.{Persisting, Querying}
import com.google.inject.ImplementedBy
import domain.recipes.{Recipe, RecipeInput, RecipeUpdateInput}

@ImplementedBy(classOf[RecipeFacade])
trait RecipeApi
    extends Persisting[Recipe, RecipeInput, RecipeUpdateInput]
    with Querying[Recipe]
