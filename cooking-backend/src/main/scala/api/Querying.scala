package api

import context.ApiContext
import domain.filters.Filters
import zio.ZIO

import java.util.UUID

trait Querying[Entity] extends Listing[Entity] {
  def getById(id: UUID): ZIO[ApiContext, Throwable, Entity]
}

trait Listing[Entity] {
  def list(query: Filters): ZIO[ApiContext, Throwable, Seq[Entity]]
}
