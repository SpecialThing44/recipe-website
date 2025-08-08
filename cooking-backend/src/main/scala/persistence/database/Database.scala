package persistence.database

import com.google.inject.ImplementedBy

@ImplementedBy(classOf[DatabaseDb])
trait Database {
  def initialize(): Unit
  def shutdown(): Unit
}
