import scoverage.ScoverageKeys
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

import scala.util.matching.Regex

val appName = "currency-conversion"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtDistributablesPlugin)
  .settings(
    libraryDependencies ++= AppDependencies(),
    retrieveManaged := true
  )
  .settings(
    publishingSettings: _*
  )
  .settings(scalaVersion := "2.12.16")
  .settings(majorVersion := 1)
  .settings(
    routesImport ++= Seq("uk.gov.hmrc.currencyconversion.binders.DateBinder._", "java.time._")
  )
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(resolvers += Resolver.typesafeRepo("releases"))
  .settings(
    ScoverageKeys.coverageExcludedFiles := "<empty>;Reverse.*;.*Routes.*;",
    ScoverageKeys.coverageMinimumStmtTotal := 80
  )
  .settings(silencerSettings)
  .settings(PlayKeys.playDefaultPort := 9016)

lazy val silencerSettings: Seq[Setting[_]] = {

  val paramValueNeverUsed: Regex = """^(parameter value)(.*)(is never used)$""".r
  val unusedImports: Regex = """^(Unused import*)$""".r

  val silencerVersion = "1.7.9"
  Seq(
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
    ),
    scalacOptions += "-feature",
    // silence warnings on autogenerated files
    scalacOptions += "-P:silencer:pathFilters=target/.*",
    // silence implicit parameter value is never used warnings
    scalacOptions += s"-P:silencer:globalFilters=$paramValueNeverUsed",
    scalacOptions += s"-P:silencer:globalFilters=$unusedImports",
    // exclude warnings for the project directories, i.e. make builds reproducible
    scalacOptions += s"-P:silencer:sourceRoots=${baseDirectory.value.getCanonicalPath}"
  )
}
