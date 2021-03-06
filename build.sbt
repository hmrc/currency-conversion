import TestPhases.oneForkedJvmPerTest
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.addTestReportOption
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

import scala.util.matching.Regex

val appName = "currency-conversion"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test(),
    retrieveManaged := true,
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false)
  )
  .settings(
    publishingSettings: _*
  )
  .settings(scalaVersion := "2.12.12")
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
    Keys.fork in IntegrationTest := false,
    unmanagedSourceDirectories in IntegrationTest := (baseDirectory in IntegrationTest) (base => Seq(base / "it")).value,
    testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    parallelExecution in IntegrationTest := false,
    addTestReportOption(IntegrationTest, "int-test-reports"),
    routesImport ++= Seq("uk.gov.hmrc.currencyconversion.binders.DateBinder._", "java.time._")
  )
  .settings(majorVersion := 1)
  .settings(
    resolvers += Resolver.jcenterRepo
  )
  .settings(
    ScoverageKeys.coverageExcludedFiles := "<empty>;Reverse.*;.*Routes.*;",
    ScoverageKeys.coverageMinimum := 80
  ).settings(silencerSettings)

lazy val silencerSettings: Seq[Setting[_]] = {

  val paramValueNeverUsed: Regex = """^(parameter value)(.*)(is never used)$""".r
  val unusedImports: Regex = """^(Unused import*)$""".r

  val silencerVersion = "1.7.0"
  Seq(
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
    ),
    // silence warnings on autogenerated files
    scalacOptions += "-P:silencer:pathFilters=target/.*",
    // silence implicit parameter value is never used warnings
    scalacOptions += s"-P:silencer:globalFilters=$paramValueNeverUsed",
    scalacOptions += s"-P:silencer:globalFilters=$unusedImports",
    // exclude warnings for the project directories, i.e. make builds reproducible
    scalacOptions += s"-P:silencer:sourceRoots=${baseDirectory.value.getCanonicalPath}"
  )
}
