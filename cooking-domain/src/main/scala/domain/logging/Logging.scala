package domain.logging

trait Logging {
  lazy val logger: Logger = LoggerImpl(this.getClass)
}
