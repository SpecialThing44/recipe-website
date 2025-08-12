package persistence.schema

enum ConstraintType:
  case Unique, Exists

object ConstraintType {
  def toConstraintType(str: String): ConstraintType = str match {
    case "UNIQUENESS" => Unique
    case "EXISTS" => Exists // REQUIRES ENTERPRISE
  }
}
