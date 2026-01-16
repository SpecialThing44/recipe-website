package api.ingredients

import api.users.AuthenticationInteractor
import api.wiki.WikipediaCheck
import com.google.inject.Inject
import context.ApiContext
import domain.ingredients.{Ingredient, IngredientUpdateInput}
import persistence.ingredients.Ingredients
import zio.ZIO

class IngredientUpdateInteractor @Inject() (
    ingredientPersistence: Ingredients,
    wikipediaCheck: WikipediaCheck
) {
  def update(
      input: IngredientUpdateInput,
      originalIngredient: Ingredient,
  ): ZIO[ApiContext, Throwable, Ingredient] = {
    for {
      context <- ZIO.service[ApiContext]
      user <- AuthenticationInteractor.ensureIsLoggedIn(
        context.applicationContext.user
      )
      _ <- AuthenticationInteractor.ensureIsAdmin(user)
      _ <-
        if (input.wikiLink.isDefined)
          wikipediaCheck.validateWikiLink(input.wikiLink.get)
        else ZIO.unit
      updatedIngredient = IngredientAdapter.adaptUpdate(
        input,
        originalIngredient
      )
      result <- ingredientPersistence.update(
        updatedIngredient,
        originalIngredient
      )
    } yield result
  }
}
