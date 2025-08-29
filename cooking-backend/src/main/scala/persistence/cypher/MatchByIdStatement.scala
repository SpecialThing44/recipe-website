package persistence.cypher

import domain.shared.Identified

import java.util.UUID

object MatchByIdStatement {
  def apply[A <: Identified](id: UUID)(implicit graph: Graph[A]) =
    s"MATCH (${graph.nodeVar}:${graph.nodeLabel} {id: '$id'})"
}
