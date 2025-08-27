package persistence.cypher

import domain.users.User
import persistence.schema.{Constraint, Index, IndexType}

import scala.reflect.ClassTag

trait Graph[Domain: ClassTag] {
  lazy val nodeName: String = implicitly[ClassTag[Domain]].runtimeClass.getSimpleName
  lazy val varName: String = nodeName.toLowerCase
}


object Graph {
  val Indices: Seq[Index] = Vector(Index(Seq("name"), User.getClass.getSimpleName.replaceAll("$", ""), IndexType.Text))

  val Constraints: Seq[Constraint] = Vector()

}
