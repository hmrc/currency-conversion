import sbt.Setting
import scoverage.ScoverageKeys.*

object CodeCoverageSettings {

  val settings: Seq[Setting[?]] = Seq(
    coverageExcludedFiles := "<empty>;.*Routes.*;",
    coverageMinimumStmtTotal := 97,
    coverageFailOnMinimum := true,
    coverageHighlighting := true
  )
}
