package initialization.embedded

import org.mockito.Mockito.{doThrow, mockConstruction, when}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration

class EmbeddedDatabaseSpec extends AnyFlatSpec with Matchers {
  it should "propagate container startup failure instead of connecting to an existing database" in {
    val failure = new IllegalStateException("Database port is already allocated")
    val containers = mockConstruction(classOf[DatabaseContainer], (container, _) => {
      when(container.withoutAuthentication()).thenReturn(container)
      doThrow(failure).when(container).start()
    })
    try {
      val embedded = new EmbeddedDatabase(Configuration.from(Map(
        "neo4j.isEmbedded" -> true, "neo4j.image" -> "neo4j:5.26.10-community-ubi9"
      )))
      intercept[IllegalStateException](embedded.initializeIfRequired()) shouldBe failure
    } finally containers.close()
  }
}
