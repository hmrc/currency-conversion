import sbt.Setting
import scoverage.ScoverageKeys.*

object CodeCoverageSettings {

  val settings: Seq[Setting[?]] = Seq(
    coverageExcludedFiles := "<empty>;.*Routes.*;",
    coverageMinimumStmtTotal := 96,
    coverageFailOnMinimum := true,
    coverageHighlighting := true
  )
}
