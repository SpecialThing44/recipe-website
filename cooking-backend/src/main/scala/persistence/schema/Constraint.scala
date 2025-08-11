package persistence.schema

case class Constraint(
    field: String,
    label: String,
    constraintType: ConstraintType
) {
  def toCypher: String = {
    val constraintTypeStr = constraintType match {
      case ConstraintType.Exists => "IS NOT NULL"
      case ConstraintType.Unique => "IS UNIQUE"
    }
    s"CREATE CONSTRAINT IF NOT EXISTS FOR (n:$label) REQUIRE n.$field $constraintTypeStr"
  }
}
