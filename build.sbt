val appName = "currency-conversion"

ThisBuild / majorVersion := 1
ThisBuild / scalaVersion := "3.5.2"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(CodeCoverageSettings.settings)
  .settings(
    libraryDependencies ++= AppDependencies(),
    scalacOptions ++= Seq(
      "-feature",
      "-Wconf:src=routes/.*:s",
      "-language:implicitConversions",
      "-language:reflectiveCalls",
      "-language:postfixOps"
    )
  )
  .settings(
    routesImport ++= Seq("uk.gov.hmrc.currencyconversion.binders.DateBinder._", "java.time._")
  )
  .settings(PlayKeys.playDefaultPort := 9016)

addCommandAlias("scalafmtAll", "all scalafmtSbt scalafmt Test/scalafmt")
