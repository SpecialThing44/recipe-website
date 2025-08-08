package persistence

import java.util

trait Converter[Domain] {
  def convert(entity: Domain): String =
    toGraph(entity)
      .map { case (key, value) =>
        s"$key: '$value'"
      }
      .mkString(", ")

  def convertForUpdate(prefix: String, entity: Domain): String =
    toGraph(entity)
      .map { case (key, value) =>
        s"$prefix.$key = '$value'"
      }
      .mkString(", ")

  def toGraph(entity: Domain): Map[String, Object]

  def toDomain(entity: util.Map[String, AnyRef]): Domain

  val lowerPrefix = "lower"
}
