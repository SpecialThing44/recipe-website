package api

import context.ApiContext
import zio.ZIO

import java.util.UUID

trait Persisting[Entity, EntityInput, EntityUpdateInput] {
  def create(entity: EntityInput): ZIO[ApiContext, Throwable, Entity]
  def update(
      entity: EntityUpdateInput,
      originalEntity: Entity
  ): ZIO[ApiContext, Throwable, Entity]
  def delete(id: UUID): ZIO[ApiContext, Throwable, Entity]
}
