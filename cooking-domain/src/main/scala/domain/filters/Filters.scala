package domain.filters

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.util.UUID

case class Filters(
    id: Option[UUID],
    ids: Option[List[UUID]],
    belongsToUser: Option[UUID],
    savedByUser: Option[UUID],
    name: Option[StringFilter],
    aliasesOrName: Option[Seq[String]],
    email: Option[StringFilter],
    prepTime: Option[NumberFilter],
    cookTime: Option[NumberFilter],
    public: Option[Boolean],
    tags: Option[Seq[String]],
    ingredients: Option[Seq[String]],
    notIngredients: Option[Seq[String]],
    analyzedRecipe: Option[UUID],
    analyzedUser: Option[UUID],
    ingredientSimilarity: Option[SimilarityFilter],
    coSaveSimilarity: Option[SimilarityFilter],
    tagSimilarity: Option[SimilarityFilter],
    orderBy: Option[OrderBy],
    limit: Option[Int],
    page: Option[Int],
) {
  def limitAndSkipStatement: String =
    limit.map(l => s"SKIP ${page.getOrElse(0) * l} LIMIT $l").getOrElse("")
}

object Filters {
  implicit val encoder: Encoder[Filters] = deriveEncoder[Filters]
  implicit val decoder: Decoder[Filters] = deriveDecoder[Filters]

  def empty(): Filters = Filters(
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None
  )
}
