package integration

import api.RecipeApp
import api.ingredients.IngredientsFacade
import api.recipes.RecipeFacade
import api.users.UserFacade
import api.wiki.WikipediaCheck
import com.google.inject.Singleton
import context.{ApiContext, ApplicationContext, CookingApi}
import integration.stubs.FakeWikipediaCheck
import integration.support.{IntegrationIngredientSupport, IntegrationUserSupport}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import zio.{Runtime, Unsafe, ZLayer}

import java.util.UUID

@Singleton
class IntegrationTestFramework
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with IntegrationUserSupport
    with IntegrationIngredientSupport {

  given CanEqual[UUID, UUID] = CanEqual.derived
  given CanEqual[Int, Int] = CanEqual.derived
  given CanEqual[String, String] = CanEqual.derived
  given CanEqual[Boolean, Boolean] = CanEqual.derived
  given [A]: CanEqual[Option[A], Option[A]] = CanEqual.derived
  given [A]: CanEqual[Option[A], Some[A]] = CanEqual.derived
  given [A, B]: CanEqual[Seq[A], Seq[B]] = CanEqual.derived


  private val application = new GuiceApplicationBuilder()
    .configure("neo4j.isEmbedded" -> true)
    .overrides(bind[WikipediaCheck].to[FakeWikipediaCheck])
    .build()
  private val recipeApp: RecipeApp = application.injector.instanceOf[RecipeApp]
  override protected val userFacade: UserFacade = application.injector.instanceOf[UserFacade]
  override protected val ingredientsFacade: IngredientsFacade =
    application.injector.instanceOf[IngredientsFacade]
  private val recipeFacade = application.injector.instanceOf[RecipeFacade]

  override def beforeAll(): Unit = {
    recipeApp.initialize()
  }

  override def afterAll(): Unit = {
    recipeApp.shutdown()
    application.stop()
  }

  override def beforeEach(): Unit = {
    createdUsers.clear()
    createdIngredients.clear()
    loggedInUser = None
  }

  override def afterEach(): Unit = {
    createdIngredients.foreach { id =>
      Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe
          .run(
            ingredientsFacade.delete(id).provideLayer(createApiContext())
          )
          .fold(_ => (), _ => ())
      }
    }

    createdUsers.foreach { id =>
      Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe
          .run(
            userFacade.delete(id).provideLayer(createApiContext())
          )
          .fold(_ => (), _ => ())
      }
    }
  }

  override def createApiContext(): ZLayer[Any, Nothing, ApiContext] = {
    ZLayer.succeed(
      ApiContext(
        application.injector.instanceOf[CookingApi],
        ApplicationContext(loggedInUser)
      )
    )
  }
}
