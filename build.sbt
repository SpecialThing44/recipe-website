import ProjectExtensions.ProjectOps
import Libraries.{logback, *}

lazy val root = project
  .in(file("."))
  .settings(
    name := "cooking",
    scalacOptions --= Seq("-Werror"),
    scalacOptions --= Seq("-Xfatal-warnings"),
    version := "0.1.0-SNAPSHOT",
  )
  .aggregate(domain, backend)

lazy val domain =
  (project in file("cooking-domain")).commonSettings.settings(
    scalacOptions --= Seq("-Werror"),
    scalacOptions --= Seq("-Xfatal-warnings"),
    libraryDependencies ++= Seq(
      zio,
      circeCore,
      circeGeneric,
      circeParser,
      slf4j,
      logback,
      sttp
    )
  )

lazy val backend =
  (project in file("cooking-backend")).commonSettings
    .dependsOn(domain, domain % "test->test")
    .settings(
      scalacOptions --= Seq("-Werror"),
      scalacOptions --= Seq("-Xfatal-warnings"),
      libraryDependencies ++= Seq(
        jdbc,
        circeCore,
        circeGeneric,
        circeParser,
        PekkoHttp,
        PekkoActor,
        PekkoActorTyped,
        PekkoStream,
        PekkoSlf4j,
        PekkoSerializationJackson,
        play,
        playJson,
        playStreams,
        guice,
        guiceGoogle,
        GoogleHttpClient,
        GoogleHttpClientGson,
        zio,
        neo4jDriver,
        jwtScala,
        pbkdf2,
        scalaTest,
        mockito
      )
    )
