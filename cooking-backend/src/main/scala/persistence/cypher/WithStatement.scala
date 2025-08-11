package persistence.cypher

object WithStatement {
  def apply[A](implicit graph: Graph[A]) = s"WITH ${graph.varName}"
}
