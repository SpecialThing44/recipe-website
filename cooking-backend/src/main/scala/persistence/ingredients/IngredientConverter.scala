package persistence.ingredients

import domain.ingredients.Ingredient
import domain.users.User
import io.circe.syntax.EncoderOps
import persistence.Converter
import persistence.users.UserConverter

import java.util
import java.util.UUID
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.jdk.CollectionConverters.*

object IngredientConverter extends Converter[Ingredient] {
  override def toGraph(ingredient: Ingredient): Map[String, Object] =
    Map(
      "id" -> ingredient.id.toString,
      "name" -> ingredient.name,
      s"${lowerPrefix}name" -> ingredient.name.toLowerCase,
      "aliases" -> ingredient.aliases.asJson,
      "wiki_link" -> ingredient.wikiLink,
      "vegetarian" -> ingredient.vegetarian.toString,
      "vegan" -> ingredient.vegan.toString
    )

  override def toDomain(
      record: util.Map[String, AnyRef],
  ): Ingredient = {
    val user = record.get("createdBy") match {
      case userMap: util.Map[String, AnyRef] => UserConverter.toDomain(userMap)
      case _ => throw new RuntimeException("User not found for ingredient")
    }

    val tags = record.get("tags") match {
      case tagMap: Seq[String] => tagMap
      case _ => throw new RuntimeException("User not found for ingredient")
    }

    Ingredient(
      id = UUID.fromString(record.get("id").toString),
      name = record.get("name").toString,
      aliases = Option(record.get("aliases").asInstanceOf[java.util.List[String]].toSeq)
        .getOrElse(Seq.empty),
      wikiLink = record.get("wiki_link").toString,
      vegetarian = record.get("vegetarian").toString.toBoolean,
      vegan = record.get("vegan").toString.toBoolean,
      tags = tags,
      createdBy = user
    )
  }
}
