package domain.logging

import com.sun.org.slf4j.internal.{Logger, LoggerFactory}

trait Logging {
  lazy val logger: Logger = LoggerFactory.getLogger(getClass)
}
