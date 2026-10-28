package api.ai

import api.ingredients.IngredientsApi
import api.tags.TagsApi
import context.{ApiContext, ApplicationContext}
import domain.users.User
import org.mockito.Mockito.{mock, verifyNoInteractions}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import zio.{Runtime, Unsafe, ZEnvironment}

class AiFacadeSpec extends AnyFlatSpec with Matchers {
  it should "reject anonymous empty and oversized requests before loading prompt data" in {
    val ai = mock(classOf[AiInteractor])
    val ingredients = mock(classOf[IngredientsApi])
    val tags = mock(classOf[TagsApi])
    val facade = new AiFacade(ai, ingredients, tags)
    Seq(
      (None, "Soup"),
      (Some(User.empty()), " "),
      (Some(User.empty()), "a" * 20001)
    ).foreach { case (user, text) =>
      val result = Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe
          .run(
            facade
              .parseRecipe(text)
              .provideEnvironment(
                ZEnvironment(ApiContext(null, ApplicationContext(user)))
              )
              .either
          )
          .getOrThrowFiberFailure()
      }
      result.isLeft shouldBe true
    }
    verifyNoInteractions(ai, ingredients, tags)
  }
  it should "reject excess concurrent parsing without waiting for a slot" in {
    val ai = mock(classOf[AiInteractor])
    val ingredients = mock(classOf[IngredientsApi])
    val tags = mock(classOf[TagsApi])
    org.mockito.Mockito
      .when(ingredients.list(domain.filters.Filters.empty()))
      .thenReturn(zio.ZIO.succeed(Seq.empty))
    org.mockito.Mockito
      .when(tags.list(domain.filters.Filters.empty()))
      .thenReturn(zio.ZIO.succeed(Seq.empty))
    val facade = new AiFacade(ai, ingredients, tags)
    val result = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(
          (for {
            entered <- zio.Ref.make(0)
            bothEntered <- zio.Promise.make[Nothing, Unit]
            _ <- zio.ZIO.succeed {
              org.mockito.Mockito
                .when(ai.parseRecipe("Soup", Seq.empty, Seq.empty))
                .thenReturn(
                  entered
                    .updateAndGet(_ + 1)
                    .flatMap(count =>
                      bothEntered.succeed(()).when(count >= 2)
                    ) *> zio.ZIO.never
                )
            }
            first <- facade.parseRecipe("Soup").fork
            second <- facade.parseRecipe("Soup").fork
            _ <- bothEntered.await
            third <- facade.parseRecipe("Soup").either
            _ <- first.interrupt
            _ <- second.interrupt
          } yield third)
            .provideEnvironment(
              ZEnvironment(
                ApiContext(null, ApplicationContext(Some(User.empty())))
              )
            )
            .timeoutFail(
              new RuntimeException("Concurrent parsing test timed out")
            )(zio.Duration.fromSeconds(5))
        )
        .getOrThrowFiberFailure()
    }
    result.left.toOption.get.getMessage should include("busy")
  }

}
