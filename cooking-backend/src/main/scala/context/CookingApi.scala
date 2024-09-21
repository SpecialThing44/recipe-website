package context

import api.recipes.RecipeApi
import api.users.UserApi
import com.google.inject.Inject

case class CookingApi @Inject() (
    recipes: RecipeApi,
    users: UserApi
)
