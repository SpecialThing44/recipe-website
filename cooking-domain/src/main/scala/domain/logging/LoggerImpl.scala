package domain.logging

import org.slf4j.LoggerFactory

class LoggerImpl(
    slf4jLogger: org.slf4j.Logger,
    clazz: Class[?]
) extends Logger {
  override def info(msg: String): Unit = println(msg)

  override def error(msg: String): Unit = slf4jLogger.error(msg)

  override def debug(msg: String): Unit = slf4jLogger.debug(msg)

  override def warn(msg: String): Unit = slf4jLogger.warn(msg)
}

object LoggerImpl {
  def apply(clazz: Class[?]): LoggerImpl =
    new LoggerImpl(LoggerFactory.getLogger(clazz), clazz)
}
