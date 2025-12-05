// Project Information
name := "cooking-backend"
packageSummary := "Backend API"
packageDescription := "Backend API"

// SBT Plugins
enablePlugins(
  PlayScala,
  UniversalPlugin,
  UniversalDeployPlugin
)
disablePlugins(PlayLayoutPlugin)

// Running
val runBackend = taskKey[Unit]("Run the backend.")

def runBackend(params: String) =
  Def.taskDyn {
    Def.task((Compile / run).toTask(s" $params").value)
  }

runBackend := {
  runBackend("").value
}

def s(params: String) =
  Def.taskDyn {
    Def.task((Compile / run).toTask(s" $params").value)
  }

// Test Options
Test / testOptions += Tests.Argument(
  TestFrameworks.ScalaTest,
  "-u",
  "target/test-reports"
)
Test / scalacOptions += "-Wconf:cat=other-pure-statement&msg=org.scalatest.Assertion:s"

// Scoverage Configuration
coverageMinimumStmtTotal := 70
coverageFailOnMinimum := false

libraryDependencies ++= Seq(
  "com.auth0" % "java-jwt" % "4.4.0",
  "com.auth0" % "jwks-rsa" % "0.22.1"
)

topLevelDirectory := Some(name.value)
