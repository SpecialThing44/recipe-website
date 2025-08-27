package persistence.tags

import com.google.inject.Inject
import context.ApiContext
import domain.filters.Filters
import org.neo4j.driver.Result
import persistence.filters.FiltersConverter
import persistence.neo4j.Database
import zio.ZIO

import scala.jdk.CollectionConverters.*

class TagsPersistence @Inject() (database: Database) extends Tags {
  override def list(
      query: Filters
  ): ZIO[ApiContext, Throwable, Seq[String]] =
    database.readTransaction(
      s"""
         |MATCH (tag:Tag)
         |${FiltersConverter.toCypher(query, "tag")}
         |WITH collect(labels(tag)[1]) as tags
         |RETURN DISTINCT tags
         |""".stripMargin,
      (result: Result) =>
        result.asScala
          .map(record => {
            record.get("tags").asList().asScala.map(_.toString).toSeq
          })
          .toSeq
          .flatten
    )
}
