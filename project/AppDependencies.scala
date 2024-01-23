import sbt.*

object AppDependencies {

  private lazy val bootstrapPlayVersion = "8.4.0"
  private lazy val hmrcMongoVersion     = "1.6.0"

  private lazy val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % hmrcMongoVersion
  )

  private lazy val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion,
    "org.scalatest"     %% "scalatest"               % "3.2.17",
    "org.mockito"       %% "mockito-scala-scalatest" % "1.17.30"
  ).map(_ % Test)

  // only add additional dependencies here - it test inherit test dependencies above already
  val itDependencies: Seq[ModuleID]    = Seq()

  def apply(): Seq[ModuleID] = compile ++ test
}
