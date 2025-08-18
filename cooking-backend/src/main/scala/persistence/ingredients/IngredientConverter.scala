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
  val veganField = "vegan"
  val vegetarianField = "vegetarian"
  val aliasesField = "aliases"

  override def toGraph(ingredient: Ingredient): Map[String, Object] =
    Map(
      idField -> ingredient.id.toString,
      nameField -> ingredient.name,
      s"$lowerPrefix$nameField" -> ingredient.name.toLowerCase,
      aliasesField -> ingredient.aliases.map(_.toLowerCase).asJson,
      wikiLinkField -> ingredient.wikiLink.toLowerCase,
      vegetarianField -> ingredient.vegetarian.toString,
      veganField -> ingredient.vegan.toString
    )

  override def toDomain(
      record: util.Map[String, AnyRef],
  ): Ingredient = {
    val user = record.get(createdByField) match {
      case userMap: util.Map[String, AnyRef] => UserConverter.toDomain(userMap)
      case _ => User.empty()
    }

    val tags = record.get(tagsField) match {
      case tagMap: Seq[String] => tagMap
      case _ => Seq.empty
    }

    Ingredient(
      id = UUID.fromString(record.get(idField).toString),
      name = record.get(nameField).toString,
      aliases = Option(
        record.get(aliasesField).asInstanceOf[java.util.List[String]].toSeq
      )
        .getOrElse(Seq.empty),
      wikiLink = record.get(wikiLinkField).toString,
      vegetarian = record.get(vegetarianField).toString.toBoolean,
      vegan = record.get(veganField).toString.toBoolean,
      tags = tags,
      createdBy = user
    )
  }
}
