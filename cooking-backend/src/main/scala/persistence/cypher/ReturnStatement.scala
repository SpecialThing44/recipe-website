package persistence.cypher

object ReturnStatement {
  def apply[A](implicit graph: Graph[A]) = s"RETURN DISTINCT ${graph.nodeVar}"
}
