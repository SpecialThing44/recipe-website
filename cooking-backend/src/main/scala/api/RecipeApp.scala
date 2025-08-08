package api

import api.database.PersistenceApi
import com.google.inject.{Inject, Singleton}
import domain.logging.Logging

@Singleton
case class RecipeApp @Inject (private val database: PersistenceApi)
    extends Logging {
  def initialize(): Unit = {
    logger.debug("Initializing recipe app.")
    database.initialize()
    logger.debug("Finished initializing recipe app.")
  }
  def shutdown(): Unit = {
    logger.debug("Shutting down recipe app.")
    database.shutdown()
    logger.debug("Finished shutting down recipe app.")
  }
}
