package persistence.database

import com.google.inject.Inject

private[persistence] class DatabaseDb @Inject() (database: persistence.neo4j.Database) extends Database {
  def initialize(): Unit = database.initialize()
  def shutdown(): Unit = database.shutdown()
}
