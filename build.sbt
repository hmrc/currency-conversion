val appName = "currency-conversion"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(
    // To resolve a bug with version 2.x.x of the scoverage plugin - https://github.com/sbt/sbt/issues/6997
    libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always,
    libraryDependencies ++= AppDependencies(),
    scalacOptions ++= Seq(
      "-feature",
      "-Wconf:src=routes/.*:s",
      "-language:implicitConversions",
      "-language:reflectiveCalls",
      "-language:postfixOps"
    )
  )
  .settings(scalaVersion := "2.13.12")
  .settings(majorVersion := 1)
  .settings(
    routesImport ++= Seq("uk.gov.hmrc.currencyconversion.binders.DateBinder._", "java.time._")
  )
  .settings(
    coverageExcludedFiles := "<empty>;.*Routes.*;",
    coverageMinimumStmtTotal := 96,
    coverageFailOnMinimum := true
  )
  .settings(PlayKeys.playDefaultPort := 9016)

addCommandAlias("scalafmtAll", "all scalafmtSbt scalafmt Test/scalafmt")
addCommandAlias("scalastyleAll", "all scalastyle Test/scalastyle")
