import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"        %% "bootstrap-backend-play-28" % "5.24.0",
    "uk.gov.hmrc.mongo"  %% "hmrc-mongo-play-28"        % "0.64.0",
    "org.reactivemongo"  %% "reactivemongo-akkastream"  % "1.0.10"
  )

  def test(scope: String = "test,it"): Seq[ModuleID] = Seq(
    "org.scalatest"           %% "scalatest"          % "3.2.9" % scope,
    "org.pegdown"             %  "pegdown"            % "1.6.0" % scope,
    "org.mockito"             %  "mockito-all"        % "2.0.2-beta" % "test",
    "org.scalatestplus"       %% "mockito-3-4"        % "3.2.9.0",
    "org.scalatestplus.play"  %% "scalatestplus-play" % "5.1.0" % "test,it",
    "com.typesafe.play"       %% "play-test"          % PlayVersion.current % scope,
    "com.github.tomakehurst"   %  "wiremock-standalone" % "2.27.2",
    "com.github.netcrusherorg" %  "netcrusher-core"          % "0.10",
    "com.vladsch.flexmark"     %  "flexmark-all"          % "0.36.8"
  )

}
