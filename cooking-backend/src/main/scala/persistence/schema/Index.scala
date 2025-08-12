package persistence.schema

case class Index(
    fields: Seq[String],
    label: String,
    indexType: IndexType
) {
  def toCypher: String = {
    val indexTypeStr = indexType match {
      case IndexType.Text  => "TEXT"
      case IndexType.Range => "RANGE"
    }
    s"CREATE $indexTypeStr INDEX IF NOT EXISTS FOR (n:$label) ON (${fields.map(f => s"n.$f").mkString(",")})"
  }
}
