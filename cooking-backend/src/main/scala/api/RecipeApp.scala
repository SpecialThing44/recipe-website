package api

import api.database.PersistenceApi
import com.google.inject.{Inject, Singleton}
import domain.logging.Logging

@Singleton
case class RecipeApp @Inject (private val database: PersistenceApi)
    extends Logging {
  def initialize(): Unit = {
    logger.info("Initializing recipe app.")
    database.initialize()
    logger.info("Finished initializing recipe app.")
  }
  def shutdown(): Unit = {
    logger.info("Shutting down recipe app.")
    database.shutdown()
    logger.info("Finished shutting down recipe app.")
  }
}
