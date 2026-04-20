package persistence.filters.base

import domain.filters.Filters
import persistence.filters.{
  CypherFragment,
  NumberFilterConverter,
  StringFilterConverter
}
import persistence.users.UserConverter.lowerPrefix

import scala.jdk.CollectionConverters.SeqHasAsJava

object FiltersNonMatchingClauses {
  private def idClause(
      filters: Filters,
      nodeVar: String
  ): Option[CypherFragment] =
    filters.id.map(id => {
      val param = s"${nodeVar}_id"
      CypherFragment(s"$nodeVar.id = $$${param}", Map(param -> id.toString))
    })

  private def idsClause(
      filters: Filters,
      nodeVar: String
  ): Option[CypherFragment] =
    filters.ids.map(ids => {
      val param = s"${nodeVar}_ids"
      CypherFragment(
        s"$nodeVar.id IN $$${param}",
        Map(param -> ids.map(_.toString).asJava)
      )
    })

  private def nameClause(
      filters: Filters,
      nodeVar: String
  ): Option[CypherFragment] =
    filters.name.map(nameFilter =>
      StringFilterConverter.toCypher(
        nameFilter,
        s"${lowerPrefix}name",
        nodeVar,
        s"${nodeVar}_name"
      )
    )

  private def emailClause(
      filters: Filters,
      nodeVar: String
  ): Option[CypherFragment] =
    filters.email.map(emailFilter =>
      StringFilterConverter.toCypher(
        emailFilter,
        s"${lowerPrefix}email",
        nodeVar,
        s"${nodeVar}_email"
      )
    )

  private def aliasesOrNameClause(
      filters: Filters,
      nodeVar: String
  ): Option[CypherFragment] =
    filters.aliasesOrName.map(aliasesList => {
      val param = s"${nodeVar}_aliases_or_name"
      CypherFragment(
        s"ANY(searchTerm IN $$${param} WHERE $nodeVar.lowername CONTAINS searchTerm OR " +
          s"($nodeVar.aliases IS NOT NULL AND ANY(alias IN $nodeVar.aliases WHERE alias CONTAINS searchTerm)))",
        Map(param -> aliasesList.map(_.toLowerCase).asJava)
      )
    })

  private def prepTimeClause(
      filters: Filters,
      nodeVar: String
  ): Option[CypherFragment] =
    filters.prepTime.map(prepTimeFilter =>
      NumberFilterConverter.toCypher(
        prepTimeFilter,
        "prepTime",
        nodeVar,
        s"${nodeVar}_prepTime"
      )
    )

  private def cookTimeClause(
      filters: Filters,
      nodeVar: String
  ): Option[CypherFragment] =
    filters.cookTime.map(cookTimeFilter =>
      NumberFilterConverter.toCypher(
        cookTimeFilter,
        "cookTime",
        nodeVar,
        s"${nodeVar}_cookTime"
      )
    )

  def nonMatchingFilters(
      filters: Filters,
      nodeVar: String
  ): Seq[Option[CypherFragment]] =
    Seq(
      idClause(filters, nodeVar),
      idsClause(filters, nodeVar),
      nameClause(filters, nodeVar),
      emailClause(filters, nodeVar),
      prepTimeClause(filters, nodeVar),
      cookTimeClause(filters, nodeVar),
      aliasesOrNameClause(filters, nodeVar)
    )
}
