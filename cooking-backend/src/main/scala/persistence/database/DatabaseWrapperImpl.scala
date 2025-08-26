package persistence.database

import com.google.inject.Inject
import zio.ZIO

private[persistence] class DatabaseWrapperImpl @Inject() (
    database: persistence.neo4j.Database
) extends DatabaseWrapper {
  def initialize(): ZIO[Any, Throwable, Unit] = database.initialize()
  def shutdown(): Unit = database.shutdown()
}
