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

runBackend := {
  runBackend("").value
}

def runBackend(params: String) =
  Def.taskDyn {
    Def.task((Compile / run).toTask(s" $params").value)
  }

// Test Options
Test / testOptions += Tests.Argument(
  TestFrameworks.ScalaTest,
  "-u",
  "target/test-reports"
)

// Scoverage Configuration
coverageMinimumStmtTotal := 70
coverageFailOnMinimum := false

topLevelDirectory := Some(name.value)
