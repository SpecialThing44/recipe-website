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

  def toGraph(entity: Domain): Map[String, Object]

  def toDomain(entity: util.Map[String, AnyRef]): Domain
}
