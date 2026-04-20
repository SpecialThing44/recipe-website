package persistence.filters

object CypherSupport {
  def mergeParams(fragments: Seq[CypherFragment]): Map[String, AnyRef] =
    fragments.foldLeft(Map.empty[String, AnyRef])((acc, fragment) =>
      acc ++ fragment.params
    )

  def nonEmpty(fragment: CypherFragment): Boolean =
    fragment.cypher.trim.nonEmpty
}
