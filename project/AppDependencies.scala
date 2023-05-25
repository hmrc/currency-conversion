import sbt.*

object AppDependencies {

  private lazy val bootstrapPlayVersion = "7.15.0"
  private lazy val hmrcMongoVersion     = "0.74.0"

  private lazy val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % hmrcMongoVersion
  )

  private lazy val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"           %% "bootstrap-test-play-28"  % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo"     %% "hmrc-mongo-test-play-28" % hmrcMongoVersion,
    "org.scalatest"         %% "scalatest"               % "3.2.16",
    "org.mockito"           %% "mockito-scala-scalatest" % "1.17.14",
    "com.github.tomakehurst" % "wiremock-standalone"     % "2.27.2",
    "com.vladsch.flexmark"   % "flexmark-all"            % "0.64.8"
  ).map(_ % Test)

  def apply(): Seq[ModuleID]           = compile ++ test
}
