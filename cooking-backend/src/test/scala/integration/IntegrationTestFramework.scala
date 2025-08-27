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


  // Use a shared test application to ensure a single database across all integration tests
  private val application = TestAppHolder.application
  private val recipeApp: RecipeApp = TestAppHolder.recipeApp
  override protected val userFacade: UserFacade = TestAppHolder.userFacade
  override protected val ingredientsFacade: IngredientsFacade = TestAppHolder.ingredientsFacade
  private val recipeFacade = TestAppHolder.recipeFacade

  override def beforeAll(): Unit = {
    // Initialize once for the entire test run; safe to call multiple times
    TestAppHolder.initOnce()
  }

  override def afterAll(): Unit = {
    // Do not shutdown here to avoid tearing down DB for other test suites.
    // Shutdown is handled by a JVM shutdown hook in TestAppHolder.
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
