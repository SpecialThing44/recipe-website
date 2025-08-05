package persistence.cypher

import scala.reflect.ClassTag

trait Graph[Domain: ClassTag] {
  lazy val nodeName: String = implicitly[ClassTag[Domain]].runtimeClass.getSimpleName
  lazy val varName: String = nodeName.toLowerCase
}
