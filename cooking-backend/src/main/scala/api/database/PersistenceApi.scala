package api.database

import com.google.inject.ImplementedBy

@ImplementedBy(classOf[PersistenceFacade])
trait PersistenceApi {
  def initialize(): Unit
  def shutdown(): Unit
}
