package persistence.filters

object RemoveSpaces {
  def apply(string: String): String =
    string.replaceAll("\n", "").replaceAll("\\s+", " ")
}
