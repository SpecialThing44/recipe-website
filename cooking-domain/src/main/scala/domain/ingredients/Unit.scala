package domain.ingredients

import domain.shared.Wikified
import domain.types.InputError
import io.circe.{Decoder, Encoder, HCursor, Json}

enum Unit(val name: String, val isVolume: Boolean, val wikiLink: String) extends Wikified:
  case Cup extends Unit("cup", true, "")
  case Milliliter extends Unit("milliliter", true, "")
  case Liter extends Unit("liter", true, "")
  case Teaspoon extends Unit("teaspoon", true, "")
  case Tablespoon extends Unit("tablespoon", true, "")
  case Piece extends Unit("piece", false, "")
  case Gram extends Unit("gram", false, "")
  case Kilogram extends Unit("kilogram", false, "")
  case Ounce extends Unit("ounce", false, "")
  case Pound extends Unit("pound", false, "")

object Unit:
  private val predefinedUnits: Seq[Unit] =
    Seq(Cup, Milliliter, Liter, Teaspoon, Tablespoon, Piece, Gram, Kilogram, Ounce, Pound)

  private val unitsByName: Map[String, Unit] = predefinedUnits.map(u => u.name -> u).toMap

  def fromName(unitName: String): Option[Unit] = unitsByName.get(unitName.toLowerCase)

  def isPredefined(unit: Unit): Boolean = predefinedUnits.contains(unit)

  def apply(name: String, volume: Boolean, wikiLink: String): Unit =
    fromName(name) match {
      case Some(unit) => unit
      case None => throw  InputError(s"Unknown unit: $name")
    }

  implicit val encoder: Encoder[Unit] = Encoder.instance(unitValue =>
    Json.obj(
      ("name", Json.fromString(unitValue.name)),
      ("volume", Json.fromBoolean(unitValue.isVolume)),
      ("wikiLink", Json.fromString(unitValue.wikiLink))
    )
  )

  implicit val decoder: Decoder[Unit] = Decoder.instance((cursor: HCursor) =>
    for
      name <- cursor.downField("name").as[String]
      volume <- cursor.downField("volume").as[Boolean]
      wiki <- cursor.downField("wikiLink").as[String]
    yield apply(name, volume, wiki)
  )
