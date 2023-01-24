import sbt._

object AppDependencies {

  private lazy val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % "7.12.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % "0.74.0"
  )

  private lazy val scope = Test

  private lazy val test: Seq[ModuleID] = Seq(
    "org.scalatest"           %% "scalatest"               % "3.2.15",
    "org.mockito"             %% "mockito-scala-scalatest" % "1.17.12",
    "org.scalatestplus.play"  %% "scalatestplus-play"      % "5.1.0",
    "com.typesafe.play"       %% "play-test"               % "2.8.19",
    "com.github.tomakehurst"   % "wiremock-standalone"     % "2.27.2",
    "com.github.netcrusherorg" % "netcrusher-core"         % "0.10",
    "com.vladsch.flexmark"     % "flexmark-all"            % "0.62.2"
  ).map(_ % scope)

  def apply(): Seq[ModuleID]           = compile ++ test

}
