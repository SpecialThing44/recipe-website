package persistence

import java.util

trait Converter[Domain] {
  val lowerPrefix = "lower"

  def convert(entity: Domain): String =
    toGraph(entity)
      .map { case (key, value) =>
        value match {
          case str: String => s"$key: '$value'"
          case _           => s"$key: $value"
        }

      }
      .mkString(", ")

  def convertForUpdate(prefix: String, entity: Domain): String =
    toGraph(entity)
      .map { case (key, value) =>
        value match {
          case str: String => s"$prefix.$key = '$value'"
          case _           => s"$prefix.$key = $value"
        }

      }
      .mkString(", ")

  def toGraph(entity: Domain): Map[String, Object]

  def toDomain(entity: util.Map[String, AnyRef]): Domain
}
