import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private lazy val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"        %% "bootstrap-backend-play-28" % "6.4.0",
    "uk.gov.hmrc.mongo"  %% "hmrc-mongo-play-28"        % "0.68.0"
  )

  private lazy val scope = Test

  private lazy val test: Seq[ModuleID] = Seq(
    "org.scalatest"            %% "scalatest"               % "3.2.13",
    "org.pegdown"              %  "pegdown"                 % "1.6.0",
    "org.mockito"              %  "mockito-all"             % "2.0.2-beta",
    "org.scalatestplus"        %% "mockito-3-4"             % "3.2.10.0",
    "org.scalatestplus.play"   %% "scalatestplus-play"      % "5.1.0",
    "com.typesafe.play"        %% "play-test"               % PlayVersion.current,
    "com.github.tomakehurst"   %  "wiremock-standalone"     % "2.27.2",
    "com.github.netcrusherorg" %  "netcrusher-core"         % "0.10",
    "com.vladsch.flexmark"     %  "flexmark-all"            % "0.62.2"
  ).map(_ % scope)

  def apply(): Seq[ModuleID] = compile ++ test

}
