import sbt.*

object Libraries {
  object Versions {
    val PekkoVersion = "1.0.3"
    val PekkoHttpVersion = "1.0.1"
    val PlayVersion = "3.0.5"
    val guiceVersion = "6.0.0"
    val GoogleHttpClientVersion = "1.45.0"
    val ZioVersion = "2.1.9"
    val ZioCatsVersion = "23.1.0.3"
    val circeVersion = "0.14.10"
    val neo4jDriverVersion = "5.26.1"
    val jwtVersion = "10.0.1"

  }
  import Versions.*
  // Pekko
  val PekkoActor = "org.apache.pekko" %% "pekko-actor" % PekkoVersion
  val PekkoTestKit = "org.apache.pekko" %% "pekko-testkit" % PekkoVersion
  val PekkoStream = "org.apache.pekko" %% "pekko-stream" % PekkoVersion

  // Pekko HTTP
  val PekkoHttp = "org.apache.pekko" %% "pekko-http" % PekkoHttpVersion
  val play = "org.playframework" %% "play" % PlayVersion
  val playJson = "org.playframework" %% "play-json" % "3.0.4"
  val playStreams = "org.playframework" %% "play-streams" % PlayVersion

  val guiceGoogle = "com.google.inject" % "guice" % guiceVersion
  val GoogleHttpClient =
    "com.google.http-client" % "google-http-client" % GoogleHttpClientVersion
  val GoogleHttpClientGson =
    "com.google.http-client" % "google-http-client-gson" % GoogleHttpClientVersion
  val zio = "dev.zio" %% "zio" % ZioVersion
  val zioCats = "dev.zio" %% "zio-interop-cats" % ZioCatsVersion

  val circeCore = "io.circe" %% "circe-core" % circeVersion
  val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  val circeParser = "io.circe" %% "circe-parser" % circeVersion

  val neo4jDriver =
    "org.neo4j.driver" % "neo4j-java-driver" % neo4jDriverVersion

  val jwtScala = "com.github.jwt-scala" %% "jwt-circe" % jwtVersion

}
