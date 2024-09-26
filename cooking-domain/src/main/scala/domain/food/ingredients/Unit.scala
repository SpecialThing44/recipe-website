package domain.food.ingredients

import domain.shared.Wikified
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class Unit(
    name: String,
    volume: Boolean,
    wikiLink: String
) extends Wikified

object Unit {
  implicit val encoder: Encoder[Unit] =
    deriveEncoder[Unit]
  implicit val decoder: Decoder[Unit] =
    deriveDecoder[Unit]
}
