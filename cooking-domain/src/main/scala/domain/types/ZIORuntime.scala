package domain.types

import zio.{Fiber, Unsafe, ZIO}

object ZIORuntime {

  def unsafeRun[E, A](app: ZIO[Any, E, A]): A =
    Unsafe.unsafe(implicit u =>
      zio.Runtime.default.unsafe
        .run(
          app
        )
        .getOrThrowFiberFailure()
    )

  def unsafeFork[E, A](app: ZIO[Any, E, A]): Fiber.Runtime[E, A] =
    Unsafe.unsafe(implicit u =>
      zio.Runtime.default.unsafe
        .fork(
          app
        )
    )
}
