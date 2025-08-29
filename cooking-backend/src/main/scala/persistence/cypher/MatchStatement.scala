package persistence.cypher

object MatchStatement {
  def apply[A](implicit graph: Graph[A]) =
    s"MATCH (${graph.nodeVar}:`${graph.nodeLabel}`)"
}
