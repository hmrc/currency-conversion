import sbt.Setting
import scoverage.ScoverageKeys.*

object CodeCoverageSettings {

  private val excludedPackages: Seq[String] =
    Seq(
      "<empty>",
      ".*Routes.*"
    )

  val settings: Seq[Setting[?]] = Seq(
    coverageExcludedPackages := excludedPackages.mkString(";"),
    coverageMinimumStmtTotal := 96,
    coverageFailOnMinimum := true,
    coverageHighlighting := true
  )
}
