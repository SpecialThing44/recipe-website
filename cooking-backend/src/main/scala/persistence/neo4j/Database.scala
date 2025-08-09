package persistence.neo4j

import com.google.inject.ImplementedBy
import org.neo4j.driver.Result

@ImplementedBy(classOf[Neo4jDatabase])
private[persistence] trait Database {
  def initialize(): Unit
  def shutdown(): Unit
  def readTransaction[A](cypher: String, logic: Result => A): zio.Task[A]
  def writeTransaction[A](cypher: String, logic: Result => A): zio.Task[A]
}
