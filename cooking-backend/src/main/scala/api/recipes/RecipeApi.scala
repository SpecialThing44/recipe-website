package api.recipes
import api.Querying
import domain.food.recipes.Recipe
import persistence.Persisting

trait RecipeApi extends Persisting[Recipe] with Querying[Recipe]
