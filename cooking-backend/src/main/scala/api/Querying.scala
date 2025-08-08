package api

import context.ApiContext
import domain.filters.Filters
import zio.ZIO

import java.util.UUID

trait Querying[Entity] {
  def list(query: Filters): ZIO[ApiContext, Throwable, Seq[Entity]]
  def getById(id: UUID): ZIO[ApiContext, Throwable, Entity]
}
