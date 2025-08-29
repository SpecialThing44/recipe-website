package persistence.cypher

trait TagPathing {
  lazy val tagLabel: String = "Tag"
  lazy val tagVar: String = tagLabel.toLowerCase
  lazy val tagRelation: String = "HAS_TAG"

  def createTagStatementsFor[A](nodeVar: String, tagRelation: String, tagLabel: String, tags: Seq[String], includeWithUser: Boolean, useAliasSuffix: Boolean)(implicit graph: Graph[A]): String =
    tags
      .map(tag => {
        val alias = if (useAliasSuffix) s"$tag" else "tag"
        val withLine = if (includeWithUser) s"\n${WithStatement.apply}, user\n" else "\n"
        s"""
           |MERGE (%s:%s {name: '%s', lowername: '%s'})
           |CREATE (%s)-[:%s]->(%s)
           |%s""".stripMargin.format(alias, tagLabel, tag, tag.toLowerCase, nodeVar, tagRelation, alias, withLine)
      })
      .mkString("\n")
}
