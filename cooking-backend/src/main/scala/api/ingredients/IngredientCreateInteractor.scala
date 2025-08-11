package api.ingredients

import api.users.AuthenticationInteractor
import api.wiki.WikipediaCheck
import api.wiki.WikipediaCheck.validateWikiLink
import com.google.inject.Inject
import context.ApiContext
import domain.ingredients.{Ingredient, IngredientInput}
import domain.types.InputError
import persistence.ingredients.Ingredients
import zio.ZIO

class IngredientCreateInteractor @Inject() (
    persistence: Ingredients,
) {
  def create(
      input: IngredientInput,
  ): ZIO[ApiContext, Throwable, Ingredient] = {
    for {
      maybeUser <- ZIO.service[ApiContext].map(_.applicationContext.user)
      user <- AuthenticationInteractor.ensureIsLoggedIn(maybeUser)
      _ <- validateWikiLink(input.wikiLink)
      ingredient = IngredientAdapter.adapt(input, user)
      result <- persistence.create(ingredient)
    } yield result
  }

}
