package persistence.neo4j

import com.google.inject.{Inject, Singleton}
import org.neo4j.driver.{AuthTokens, Driver, GraphDatabase, Result}
import play.api.Configuration
import zio.ZIO

import scala.compiletime.uninitialized
import scala.util.Try

@Singleton
private[persistence] case class Neo4jDatabase @Inject() (config: Configuration)
    extends Database {
  private var driver: Driver = uninitialized

  override def initialize(): Unit = {
    val uri = config.get[String]("neo4j.uri")
    val username = config.get[String]("neo4j.username")
    val password = config.get[String]("neo4j.password")
    driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password))
  }

  override def shutdown(): Unit = driver.close()

  override def writeTransaction(cypher: String): zio.Task[Result] =
    ZIO.fromTry {
      Try {
        val session = driver.session
        session.run("MATCH (n) DETACH DELETE n")
        session.executeWrite(tx => tx.run(cypher))
      }
    }

  override def readTransaction(cypher: String): zio.Task[Result] =
    ZIO.fromTry {
      Try {
        val session = driver.session
        session.run("MATCH (n) DETACH DELETE n")
        session.executeRead(tx => tx.run(cypher))
      }
    }
}
