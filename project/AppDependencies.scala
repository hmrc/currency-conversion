import sbt.*

object AppDependencies {

  private lazy val bootstrapPlayVersion = "7.23.0"
  private lazy val hmrcMongoVersion     = "1.6.0"

  private lazy val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % hmrcMongoVersion
  )

  private lazy val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"         %% "bootstrap-test-play-28"  % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo"   %% "hmrc-mongo-test-play-28" % hmrcMongoVersion,
    "org.scalatest"       %% "scalatest"               % "3.2.17",
    "org.mockito"         %% "mockito-scala-scalatest" % "1.17.30",
    "com.vladsch.flexmark" % "flexmark-all"            % "0.64.8"
  ).map(_ % Test)

  def apply(): Seq[ModuleID]           = compile ++ test
}
