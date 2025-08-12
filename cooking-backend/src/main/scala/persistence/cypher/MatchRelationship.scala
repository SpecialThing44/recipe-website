package persistence.cypher

object MatchRelationship {
  def outgoing[A](
      relationshipLabel: String,
      matchedVarName: String,
      matchedNodeName: String
  )(implicit graph: Graph[A]) =
    s"MATCH (${graph.varName})-[:$relationshipLabel]->($matchedVarName:$matchedNodeName)"

}
