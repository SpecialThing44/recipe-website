package persistence.database

import com.google.inject.ImplementedBy
import zio.ZIO

@ImplementedBy(classOf[DatabaseWrapperImpl])
trait DatabaseWrapper {
  def initialize(): ZIO[Any, Throwable, Unit]
  def shutdown(): Unit
}
