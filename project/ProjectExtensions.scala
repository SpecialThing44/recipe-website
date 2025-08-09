import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.*
import sbt.*
import sbt.Keys.*

object ProjectExtensions {
  val cores: Int = java.lang.Runtime.getRuntime.availableProcessors
  val defaultScalaVersion = "3.5.0"

  val additionalScalacOptions = Seq(
    "-language:postfixOps", // allow postfix ops without `import scala.language.postfixOps`,
  )

  implicit final class DepsOps(deps: Seq[ModuleID]) {
    def testDeps: Seq[ModuleID] = deps.map(_ % Test)
    def testClassifierDeps: Seq[ModuleID] = testDeps.map(_ classifier "tests")
  }

  implicit final class ProjectOps(project: Project) {
    def commonSettings: Project =
      project.settings(
        scalaVersion := defaultScalaVersion,
        libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % "always",
        scalacOptions ++= additionalScalacOptions,
        Compile / doc / sources := Seq.empty,
        Compile / packageDoc / publishArtifact := false,
        Test / fork := true,
        Test / testForkedParallel := true,
        Test / parallelExecution := true,
        Test / concurrentRestrictions := Seq(
          Tags.limit(Tags.ForkedTestGroup, cores)
        ),
      )

  }
}
