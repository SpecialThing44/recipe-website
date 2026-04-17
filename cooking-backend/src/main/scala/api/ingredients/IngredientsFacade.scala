package api.ingredients

import api.users.AuthenticationInteractor
import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import domain.ingredients.{Ingredient, IngredientInput, IngredientUpdateInput}
import domain.types.InputError
import persistence.ingredients.Ingredients
import zio.ZIO

import java.util.UUID

class IngredientsFacade @Inject() (
    persistence: Ingredients,
    createInteractor: IngredientCreateInteractor,
    updateInteractor: IngredientUpdateInteractor,
    deleteInteractor: IngredientDeleteInteractor,
    fetchInteractor: IngredientFetchInteractor,
) extends IngredientsApi {

  override def create(
      entity: IngredientInput
  ): ZIO[ApiContext, Throwable, Ingredient] = {
    createInteractor.create(entity)
  }

  override def update(
      entity: IngredientUpdateInput,
      originalEntity: Ingredient
  ): ZIO[ApiContext, Throwable, Ingredient] = {
    updateInteractor.update(entity, originalEntity)
  }

  override def delete(id: UUID): ZIO[ApiContext, Throwable, Ingredient] = {
    deleteInteractor.delete(id)
  }

  override def list(
      query: Filters
  ): ZIO[ApiContext, Throwable, Seq[Ingredient]] = {
    fetchInteractor.list(query)
  }

  override def getById(id: UUID): ZIO[ApiContext, Throwable, Ingredient] = {
    persistence.getById(id)
  }

  override def deleteAll(): ZIO[ApiContext, Throwable, Unit] = {
    persistence.deleteAll()
  }

  override def listSubstitutes(
      id: UUID
  ): ZIO[ApiContext, Throwable, Seq[Ingredient]] =
    persistence.listSubstitutes(id)

  override def addSubstitute(
      id: UUID,
      substituteId: UUID
  ): ZIO[ApiContext, Throwable, Unit] = {
    for {
      maybeUser <- ZIO.service[ApiContext].map(_.applicationContext.user)
      user <- AuthenticationInteractor.ensureIsLoggedIn(maybeUser)
      _ <- AuthenticationInteractor.ensureIsAdmin(user)
      _ <-
        if (id == substituteId)
          ZIO.fail(InputError("Ingredient cannot be a substitute of itself"))
        else ZIO.unit
      _ <- persistence.getById(id)
      _ <- persistence.getById(substituteId)
      _ <- persistence.addSubstitute(id, substituteId)
    } yield ()
  }

  override def removeSubstitute(
      id: UUID,
      substituteId: UUID
  ): ZIO[ApiContext, Throwable, Unit] = {
    for {
      maybeUser <- ZIO.service[ApiContext].map(_.applicationContext.user)
      user <- AuthenticationInteractor.ensureIsLoggedIn(maybeUser)
      _ <- AuthenticationInteractor.ensureIsAdmin(user)
      _ <-
        if (id == substituteId)
          ZIO.fail(InputError("Ingredient cannot be a substitute of itself"))
        else ZIO.unit
      _ <- persistence.getById(id)
      _ <- persistence.getById(substituteId)
      _ <- persistence.removeSubstitute(id, substituteId)
    } yield ()
  }
}
