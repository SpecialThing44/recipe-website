package api.database

import com.google.inject.Inject
import persistence.database.DatabaseWrapper
import zio.ZIO

class PersistenceFacade @Inject() (val persistence: DatabaseWrapper)
    extends PersistenceApi {

  override def initialize(): ZIO[Any, Throwable, Unit] =
    persistence.initialize()

  override def shutdown(): Unit = persistence.shutdown()
}
