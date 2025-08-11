package persistence.schema

enum IndexType:
  case Text, Range, Lookup

object IndexType {
  def toIndexType(str: String): IndexType = str match {
    case "TEXT" => Text
    case "RANGE" => Range
    case "LOOKUP" => Lookup
  }
}
