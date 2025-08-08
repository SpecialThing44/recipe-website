package initialization

import com.google.inject.AbstractModule
import domain.logging.Logging

class InitializationModule extends AbstractModule with Logging {
  override def configure(): Unit = {
    logger.debug("Creating init bindings.")
    bind(classOf[Startup]).asEagerSingleton()
    logger.debug("Finished creating init bindings.")
  }
}
