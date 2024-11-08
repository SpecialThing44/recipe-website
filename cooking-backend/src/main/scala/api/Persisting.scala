package api

import context.ApiContext
import zio.ZIO

import java.util.UUID

trait Persisting[Entity] {
  def create(entity: Entity): ZIO[ApiContext, Throwable, Entity]
  def update(
      entity: Entity,
      originalEntity: Entity
  ): ZIO[ApiContext, Throwable, Entity]
  def delete(id: UUID): ZIO[ApiContext, Throwable, Entity]
}
