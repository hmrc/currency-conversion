import sbt.*

object AppDependencies {

  private lazy val bootstrapPlayVersion = "8.4.0"
  private lazy val hmrcMongoVersion     = "1.7.0"

  private lazy val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % hmrcMongoVersion
  )

  private lazy val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion,
    "org.mockito"       %% "mockito-scala-scalatest" % "1.17.30"
  ).map(_ % Test)

  def apply(): Seq[ModuleID]           = compile ++ test
}
