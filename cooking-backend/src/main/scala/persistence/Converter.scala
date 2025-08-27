package persistence

import java.util

trait Converter[Domain] {
  val lowerPrefix = "lower"
  val idField = "id"
  val createdOnField = "createdOn"
  val updatedOnField = "updatedOn"
  val nameField = "name"
  val wikiLinkField = "wikiLink"
  val createdByField = "createdBy"
  val tagsField = "tags"

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
