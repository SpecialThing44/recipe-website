package persistence

import context.ApiContext
import zio.ZIO

trait Persisting[Entity] {
  def create(entity: Entity): ZIO[ApiContext, Throwable, Entity]
  def update(
      entity: Entity,
      originalEntity: Entity
  ): ZIO[ApiContext, Throwable, Entity]
}
