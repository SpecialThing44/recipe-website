package persistence.filters

trait Cypher[A] {
  def toCypher(filter: A, property: String, nodeVar: String): String
}
