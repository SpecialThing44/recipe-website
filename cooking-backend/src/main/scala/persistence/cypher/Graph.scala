package persistence.cypher

import scala.reflect.ClassTag

trait Graph[Domain: ClassTag] {
  val nodeName: String = implicitly[ClassTag[Domain]].runtimeClass.getSimpleName
  val varName: String = nodeName.toLowerCase
}
