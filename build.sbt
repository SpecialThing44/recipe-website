import ProjectExtensions.ProjectOps

import Libraries.*

lazy val root = project
  .in(file("."))
  .settings(
    name := "cooking",
    scalacOptions --= Seq("-Werror"),
    scalacOptions --= Seq("-Xfatal-warnings"),
    version := "0.1.0-SNAPSHOT",
    libraryDependencies += "org.scalameta" %% "munit" % "1.0.0" % Test
  )
  .aggregate(domain, backend)

lazy val domain =
  (project in file("cooking-domain")).commonSettings.settings(
    scalacOptions --= Seq("-Werror"),
    scalacOptions --= Seq("-Xfatal-warnings"),
    libraryDependencies ++= Seq(
      zio,
      zioCats,
      circeCore,
      circeGeneric,
      circeParser
    )
  )

lazy val backend =
  (project in file("cooking-backend")).commonSettings
    .dependsOn(domain, domain % "test->test")
    .settings(
      scalacOptions --= Seq("-Werror"),
      scalacOptions --= Seq("-Xfatal-warnings"),
      libraryDependencies ++= Seq(
        circeCore,
        circeGeneric,
        circeParser,
        PekkoHttp,
        PekkoActor,
        PekkoStream,
        play,
        playJson,
        playStreams,
        guice,
        guiceGoogle,
        GoogleHttpClient,
        GoogleHttpClientGson,
        zio,
        zioCats,
      )
    )
