package persistence.recipes

import domain.ingredients.{InstructionIngredient, Quantity, Unit}
import domain.recipes.Recipe
import persistence.Converter
import persistence.ingredients.IngredientConverter
import persistence.users.UserConverter

import java.time.Instant
import java.util
import java.util.UUID
import scala.jdk.CollectionConverters.*

object RecipeConverter extends Converter[Recipe] {
  val prepTimeField = "prepTime"
  val cookTimeField = "cookTime"
  val vegetarianField = "vegetarian"
  val veganField = "vegan"
  val countryOfOriginField = "countryOfOrigin"
  val publicField = "public"
  val instructionsField = "instructions"

  override def toGraph(recipe: Recipe): Map[String, Object] =
    Map(
      idField -> recipe.id.toString,
      nameField -> recipe.name,
      s"${lowerPrefix}name" -> recipe.name.toLowerCase,
      prepTimeField -> Int.box(recipe.prepTime),
      cookTimeField -> Int.box(recipe.cookTime),
      vegetarianField -> recipe.vegetarian.toString,
      veganField -> recipe.vegan.toString,
      countryOfOriginField -> recipe.countryOfOrigin.getOrElse(""),
      publicField -> Boolean.box(recipe.public),
      wikiLinkField -> recipe.wikiLink.getOrElse(""),
      instructionsField -> recipe.instructions,
      createdOnField -> recipe.createdOn.toString,
      updatedOnField -> recipe.updatedOn.toString
    )

  override def toDomain(record: util.Map[String, AnyRef]): Recipe = {
    val user = record.get(createdByField) match {
      case userMap: util.Map[String, AnyRef] => UserConverter.toDomain(userMap)
      case _                                 => domain.users.User.empty()
    }

    val tags: Seq[String] = record.get(tagsField) match {
      case tagSeq: Seq[String] => tagSeq
      case _                   => Seq.empty
    }

    val ingredientNodes = record.get("ingredients") match {
      case list: java.util.List[util.Map[String, AnyRef]] =>
        list.asScala.toSeq
      case _ => Seq.empty
    }

    val amounts: Seq[Int] = record.get("amounts") match {
      case list: java.util.List[Number] =>
        list.asScala.map(_.intValue()).toSeq
      case _ => Seq.empty
    }

    val units: Seq[String] = record.get("units") match {
      case list: java.util.List[String] =>
        list.asScala.toSeq
      case _ => Seq.empty
    }

    val ingredients: Seq[InstructionIngredient] =
      ingredientNodes.zipWithIndex.map { case (ingMap, idx) =>
        val ingredient = IngredientConverter.toDomain(ingMap)
        val amount = if (idx < amounts.size) amounts(idx) else 0
        val unitName = if (idx < units.size) units(idx) else ""
        val unit = Unit(unitName, volume = false, wikiLink = "")
        InstructionIngredient(ingredient, Quantity(unit, amount))
      }

    Recipe(
      id = UUID.fromString(record.get(idField).toString),
      name = record.get(nameField).toString,
      createdBy = user,
      tags = tags,
      ingredients = ingredients,
      prepTime = record.get(prepTimeField).toString.toInt,
      cookTime = record.get(cookTimeField).toString.toInt,
      vegetarian = record.get(vegetarianField).toString.toBoolean,
      vegan = record.get(veganField).toString.toBoolean,
      countryOfOrigin =
        Option(record.get(countryOfOriginField).toString).filter(_.nonEmpty),
      public = record.get(publicField).toString.toBoolean,
      wikiLink = Option(record.get(wikiLinkField).toString).filter(_.nonEmpty),
      instructions = record.get(instructionsField).toString,
      createdOn = Instant.parse(record.get(createdOnField).toString),
      updatedOn = Instant.parse(record.get(updatedOnField).toString)
    )
  }
}
