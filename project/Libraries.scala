import sbt.*

object Libraries {
  object Versions {
    val PekkoVersion = "1.0.3"
    val PekkoHttpVersion = "1.0.1"
    val PlayVersion = "3.0.5"
    val guiceVersion = "6.0.0"
    val GoogleHttpClientVersion = "1.45.0"

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

}
