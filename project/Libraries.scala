import sbt.*

object Libraries {
  object Versions {
    val PekkoVersion = "1.7.1"
    val PekkoHttpVersion = "1.4.2"
    val PlayVersion = "3.0.12"
    val jacksonVersion = "2.22.4"
    val guiceVersion = "6.0.0"
    val GoogleHttpClientVersion = "2.2.0"
    val ZioVersion = "2.1.27"
    val circeVersion = "0.14.17"
    val neo4jDriverVersion = "6.3.0"
    val jwtVersion = "12.0.0"
    val pbkdf2Version = "0.7.2"
    val slf4jVersion = "2.0.20"
    val logbackVersion = "1.6.6"
    val sttpVersion = "4.1.0"
    val scrimageVersion = "4.6.10"

    val scalaTestVersion = "3.2.20"
    val mockitoVersion = "5.24.0"
    val testContainersVersion = "2.0.5"

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
  val playJson = "org.playframework" %% "play-json" % "3.0.6"
  val playStreams = "org.playframework" %% "play-streams" % PlayVersion

  val jacksonScala =
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion

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

  val sttp = "com.softwaremill.sttp.client4" %% "core" % sttpVersion

  val scrimageCore = "com.sksamuel.scrimage" % "scrimage-core" % scrimageVersion
  val scrimageWebp = "com.sksamuel.scrimage" % "scrimage-webp" % scrimageVersion

  // Test dependencies
  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % Test
  val mockito = "org.mockito" % "mockito-core" % mockitoVersion % Test
  val testContainers =
    "org.testcontainers" % "testcontainers" % testContainersVersion
  val testContainersNeo4j =
    "org.testcontainers" % "testcontainers-neo4j" % testContainersVersion
}
