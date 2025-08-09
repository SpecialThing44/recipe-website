package context

import api.ingredients.IngredientsApi
import api.recipes.RecipeApi
import api.users.UserApi
import com.google.inject.Inject

case class CookingApi @Inject() (
    recipes: RecipeApi,
    ingredients: IngredientsApi,
    users: UserApi
)
