package persistence.filters

final case class CypherFragment(cypher: String, params: Map[String, AnyRef])

object CypherFragment {
  val empty: CypherFragment = CypherFragment("", Map.empty)
}

trait Cypher[A] {
  def toCypher(
      filter: A,
      property: String,
      nodeVar: String,
      paramPrefix: String
  ): CypherFragment
}
