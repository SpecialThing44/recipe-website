package persistence.cypher

trait TagPathing {
  lazy val tagLabel: String = "Tag"
  lazy val tagVar: String = tagLabel.toLowerCase
  lazy val tagRelation: String = "HAS_TAG"
}
