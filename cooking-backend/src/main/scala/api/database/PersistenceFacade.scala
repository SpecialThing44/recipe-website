package api.database

import com.google.inject.Inject
import persistence.database.Database

class PersistenceFacade @Inject() (val persistence: Database)
    extends PersistenceApi {

  override def initialize(): Unit = persistence.initialize()

  override def shutdown(): Unit = persistence.shutdown()
}
