import sbt.*

object Libraries {
  object Versions {
    val PekkoVersion = "1.1.5"
    val PekkoHttpVersion = "1.2.0"
    val PlayVersion = "3.0.8"
    val guiceVersion = "6.0.0"
    val GoogleHttpClientVersion = "1.47.1"
    val ZioVersion = "2.1.20"
    val circeVersion = "0.14.14"
    val neo4jDriverVersion = "5.28.9"
    val jwtVersion = "11.0.2"
    val pbkdf2Version = "0.7.2"
    val slf4jVersion = "2.0.17"
    val logbackVersion = "1.5.25"
    val sttpVersion = "3.11.0"
    val scrimageVersion = "4.1.3"

    val scalaTestVersion = "3.2.19"
    val mockitoVersion = "5.18.0"
    val testContainersVersion = "1.21.3"

  }
  import Versions.*
  // Pekko
  val PekkoActor = "org.apache.pekko" %% "pekko-actor" % PekkoVersion
  val PekkoActorTyped = "org.apache.pekko" %% "pekko-actor-typed" % PekkoVersion
  val PekkoTestKit = "org.apache.pekko" %% "pekko-testkit" % PekkoVersion
  val PekkoStream = "org.apache.pekko" %% "pekko-stream" % PekkoVersion
  val PekkoSlf4j = "org.apache.pekko" %% "pekko-slf4j" % PekkoVersion
  val PekkoSerializationJackson =
    "org.apache.pekko" %% "pekko-serialization-jackson" % PekkoVersion

  // Pekko HTTP
  val PekkoHttp = "org.apache.pekko" %% "pekko-http" % PekkoHttpVersion
  val play = "org.playframework" %% "play" % PlayVersion
  val playJson = "org.playframework" %% "play-json" % "3.0.5"
  val playStreams = "org.playframework" %% "play-streams" % PlayVersion

  val guiceGoogle = "com.google.inject" % "guice" % guiceVersion
  val GoogleHttpClient =
    "com.google.http-client" % "google-http-client" % GoogleHttpClientVersion
  val GoogleHttpClientGson =
    "com.google.http-client" % "google-http-client-gson" % GoogleHttpClientVersion
  val zio = "dev.zio" %% "zio" % ZioVersion

  val circeCore = "io.circe" %% "circe-core" % circeVersion
  val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  val circeParser = "io.circe" %% "circe-parser" % circeVersion

  val neo4jDriver =
    "org.neo4j.driver" % "neo4j-java-driver" % neo4jDriverVersion

  val jwtScala = "com.github.jwt-scala" %% "jwt-circe" % jwtVersion
  val pbkdf2 = "io.github.nremond" %% "pbkdf2-scala" % pbkdf2Version

  val slf4j = "org.slf4j" % "slf4j-api" % slf4jVersion
  val logback = "ch.qos.logback" % "logback-classic" % logbackVersion

  val sttp = "com.softwaremill.sttp.client3" %% "core" % sttpVersion

  val scrimageCore = "com.sksamuel.scrimage" % "scrimage-core" % scrimageVersion
  val scrimageWebp = "com.sksamuel.scrimage" % "scrimage-webp" % scrimageVersion

  // Test dependencies
  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % Test
  val mockito = "org.mockito" % "mockito-core" % mockitoVersion % Test
  val testContainers =
    "org.testcontainers" % "testcontainers" % testContainersVersion
  val testContainersNeo4j =
    "org.testcontainers" % "neo4j" % testContainersVersion
}
