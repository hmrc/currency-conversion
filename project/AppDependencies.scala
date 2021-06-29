import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-play-26" % "1.14.0",
    "org.reactivemongo"          %% "play2-reactivemongo"      % "0.18.8-play26",
    "org.reactivemongo"          %% "reactivemongo-akkastream" % "0.20.3"
  )

  def test(scope: String = "test,it") = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-26" % scope, //deprecated
    "org.scalatest" %% "scalatest" % "3.0.8" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "org.mockito" % "mockito-all" % "2.0.2-beta" % "test",
    "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.3" % "test,it",
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "com.github.tomakehurst"   %  "wiremock-standalone"      % "2.27.2",
   "com.github.netcrusherorg" %  "netcrusher-core"          % "0.10",
    "org.scalatestplus.play"   %% "scalatestplus-play"       % "3.1.3"
  )

}
