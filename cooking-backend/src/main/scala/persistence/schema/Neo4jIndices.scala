package persistence.schema

import domain.logging.Logging
import org.neo4j.driver.Result
import persistence.cypher.Graph
import persistence.neo4j.Database
import persistence.schema.ConstraintType.toConstraintType
import persistence.schema.IndexType.toIndexType
import zio.ZIO

import java.util
import scala.jdk.CollectionConverters.{
  CollectionHasAsScala,
  IteratorHasAsScala,
  MapHasAsScala
}

private[persistence] case class Neo4jIndices()(implicit
    val database: Database
) extends Logging {

  def applySchema(): ZIO[Any, Throwable, Unit] = {
    for {
      indicesInDatabase <- Neo4jIndices.indicesInDatabase

      constraintsInDatabase <- Neo4jIndices.constraintsInDatabase

      _ <- ZIO.collectAll(
        Graph.Indices.map(index =>
          database
            .writeTransaction(
              index.toCypher,
              (result: Result) =>
                result.asScala
                  .map(record => record.get("").asMap())
                  .toSeq
            )
        )
      )

      _ <- ZIO.collectAll(
        Graph.Constraints.map(constraint =>
          database
            .writeTransaction(
              constraint.toCypher,
              (result: Result) =>
                result.asScala
                  .map(record => record)
            )
        )
      )
    } yield ()
  }

}

object Neo4jIndices {
  private def indicesInDatabase(implicit
      database: Database
  ): zio.Task[Seq[Index]] =
    database
      .readTransaction(
        "SHOW INDEXES yield properties, labelsOrTypes, type;",
        (result: Result) => {
          if (result.hasNext) {
            result
              .list()
              .asScala
              .map(record => {
                val mapRecord = record.asMap.asScala
                Index(
                  convert(mapRecord("properties")),
                  head(convert(mapRecord("labelsOrTypes"))),
                  toIndexType(mapRecord("type").toString)
                )
              })
              .toSeq
          } else {
            Seq.empty
          }
        }
      )

  private def convert(result: AnyRef): Seq[String] = if (result != null)
    result.asInstanceOf[util.AbstractList[String]].asScala.toSeq
  else Seq.empty

  private def head(seq: Seq[String]): String =
    if (seq.nonEmpty) seq.head else ""

  private def constraintsInDatabase(implicit
      database: Database
  ): zio.Task[Seq[Constraint]] =
    database
      .readTransaction(
        "SHOW CONSTRAINTS yield properties, labelsOrTypes, type;",
        (result: Result) => {
          if (result.hasNext) {
            result
              .list()
              .asScala
              .map(record => {
                val mapRecord = record.asMap.asScala
                Constraint(
                  head(convert(mapRecord("properties"))),
                  head(convert(mapRecord("labelsOrTypes"))),
                  toConstraintType(mapRecord("type").toString)
                )
              })
              .toSeq
          } else {
            Seq.empty
          }
        }
      )
}
