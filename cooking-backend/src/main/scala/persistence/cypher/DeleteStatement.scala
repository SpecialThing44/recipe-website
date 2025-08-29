package persistence.cypher

object DeleteStatement {
  def apply[A](implicit graph: Graph[A]) = s"DETACH DELETE ${graph.nodeVar}"
}
