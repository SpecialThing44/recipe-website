package persistence.recipes

import domain.ingredients.{InstructionIngredient, Quantity, Unit}
import domain.recipes.{ImageUrls, Recipe}
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
  val servingsField = "servings"
  val countryOfOriginField = "countryOfOrigin"
  val publicField = "public"
  val instructionsField = "instructions"
  val instructionImagesField = "instructionImages"
  val imageUrlField = "imageUrl"
  val imageThumbnailUrlField = "imageThumbnailUrl"
  val imageMediumUrlField = "imageMediumUrl"

  override def toGraph(recipe: Recipe): Map[String, Object] =
    Map(
      idField -> recipe.id.toString,
      nameField -> recipe.name,
      s"${lowerPrefix}name" -> recipe.name.toLowerCase,
      prepTimeField -> Int.box(recipe.prepTime),
      cookTimeField -> Int.box(recipe.cookTime),
      servingsField -> Int.box(recipe.servings),
      countryOfOriginField -> recipe.countryOfOrigin.getOrElse(""),
      publicField -> Boolean.box(recipe.public),
      wikiLinkField -> recipe.wikiLink.getOrElse(""),
      instructionsField -> recipe.instructions,
      instructionImagesField -> recipe.instructionImages.mkString(","),
      imageUrlField -> recipe.image.map(_.large).getOrElse(""),
      imageThumbnailUrlField -> recipe.image.map(_.thumbnail).getOrElse(""),
      imageMediumUrlField -> recipe.image.map(_.medium).getOrElse(""),
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
          case _                           => new util.HashMap[String, AnyRef]()
        }
        val ingredient = IngredientConverter.toDomain(ingMap)
        val amountAny = iq.get("amount")
        val amount = amountAny match {
          case n: Number => n.doubleValue()
          case _         => amountAny.toString.toDouble
        }
        val unitName = Option(iq.get("unit")).map(_.toString).getOrElse("")
        val unit = Unit(unitName, volume = false, wikiLink = "")
        val description =
          Option(iq.get("description")).map(_.toString).filter(_.nonEmpty)
        InstructionIngredient(ingredient, Quantity(unit, amount), description)
      }

    val thumbnail = Option(record.get(imageThumbnailUrlField))
      .map(_.toString)
      .filter(_.nonEmpty)
    val medium =
      Option(record.get(imageMediumUrlField)).map(_.toString).filter(_.nonEmpty)
    val large =
      Option(record.get(imageUrlField)).map(_.toString).filter(_.nonEmpty)

    val image = (thumbnail, medium, large) match {
      case (Some(t), Some(m), Some(l)) => Some(domain.users.AvatarUrls(t, m, l))
      case _                           => None
    }

    val instructionImages = Option(record.get(instructionImagesField))
      .map(_.toString)
      .filter(_.nonEmpty)
      .map(_.split(",").toSeq.filter(_.nonEmpty))
      .getOrElse(Seq.empty)

    Recipe(
      id = UUID.fromString(record.get(idField).toString),
      name = record.get(nameField).toString,
      createdBy = user,
      tags = tags,
      ingredients = ingredients,
      prepTime = record.get(prepTimeField).toString.toInt,
      cookTime = record.get(cookTimeField).toString.toInt,
      servings = Option(record.get(servingsField)).map(_.toString.toInt).getOrElse(1),
      countryOfOrigin =
        Option(record.get(countryOfOriginField).toString).filter(_.nonEmpty),
      public = record.get(publicField).toString.toBoolean,
      wikiLink = Option(record.get(wikiLinkField).toString).filter(_.nonEmpty),
      instructions = record.get(instructionsField).toString,
      instructionImages = instructionImages,
      image = image,
      createdOn = Instant.parse(record.get(createdOnField).toString),
      updatedOn = Instant.parse(record.get(updatedOnField).toString)
    )
  }
}
