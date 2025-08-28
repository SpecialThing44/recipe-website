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

    val ingredientQuantities = record.get("ingredientQuantities") match {
      case list: java.util.List[util.Map[String, AnyRef]] =>
        list.asScala.toSeq
      case _ => Seq.empty
    }

    val ingredients: Seq[InstructionIngredient] =
      ingredientQuantities.map { iq =>
        val ingAny = iq.get("ingredient")
        val ingMap = ingAny match {
          case m: util.Map[String, AnyRef] => m
          case _ => new util.HashMap[String, AnyRef]()
        }
        val ingredient = IngredientConverter.toDomain(ingMap)
        val amountAny = iq.get("amount")
        val amount = amountAny match {
          case n: Number => n.intValue()
          case _ => amountAny.toString.toInt
        }
        val unitName = Option(iq.get("unit")).map(_.toString).getOrElse("")
        val unit = Unit(unitName, volume = false, wikiLink = "")
        val description = Option(iq.get("description")).map(_.toString).filter(_.nonEmpty)
        InstructionIngredient(ingredient, Quantity(unit, amount), description)
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
