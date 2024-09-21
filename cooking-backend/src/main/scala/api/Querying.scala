package api

import context.ApiContext
import play.api.libs.json.JsValue
import zio.ZIO

import java.util.UUID

trait Querying[Entity] {
  def list(query: JsValue): ZIO[ApiContext, Throwable, Seq[Entity]]
  def find(query: JsValue): ZIO[ApiContext, Throwable, Entity]
  def get(id: UUID): ZIO[ApiContext, Throwable, Entity]
}
