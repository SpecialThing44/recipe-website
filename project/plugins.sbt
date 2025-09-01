ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.5")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.2.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")
addSbtPlugin("org.typelevel" % "sbt-tpolecat" % "0.5.2")
addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.3.1")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.1")
addDependencyTreePlugin
