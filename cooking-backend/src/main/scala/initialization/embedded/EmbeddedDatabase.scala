package initialization.embedded

import com.google.inject.Inject
import domain.logging.Logging
import org.testcontainers.containers.Neo4jContainer
import org.testcontainers.utility.DockerImageName
import play.api.Configuration

import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.{Failure, Success, Try}

class EmbeddedDatabase @Inject() (
    config: Configuration,
) extends Logging {
  private var database: Option[DatabaseContainer] = None
  def initializeIfRequired(): Unit =
    if (database.isEmpty && config.get[Boolean]("neo4j.isEmbedded")) {
      val container =
        new DatabaseContainer(config.get[String]("neo4j.image"))
          .withoutAuthentication()

      val ports = Seq(7687, 7474, 2004)
      val portsAsString = ports.map(port => s"$port:$port").asJava

      container.setPortBindings(portsAsString)

      database = Some(container)
      logger.info(
        s"[${this.getClass.getName}]: DB requested, starting",
      )
      Try(container.start()) match {
        case Success(_) => ()
        case Failure(_) => ()
      }
    } else {
      logger.info(
        s"[${this.getClass.getName}]: DB not requested, returning unit",
      )
    }

  def shutdown(): Unit = {
    database.foreach(_.stop())
    database = None
  }

}

class DatabaseContainer(image: String)
    extends Neo4jContainer[DatabaseContainer](
      DockerImageName
        .parse(image)
        .asCompatibleSubstituteFor("neo4j")
    )
