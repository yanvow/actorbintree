package sbt // To access the private[sbt] compilerReporter key
package filteringReporterPlugin

import Keys._
import ch.epfl.lamp._

object FilteringReporterPlugin extends AutoPlugin {
  override lazy val projectSettings = Seq(
    // Turn off warning coming from scalameter that we cannot fix without changing scalameter
    compilerReporter in (Compile, compile) ~= { reporter => new FilteringReporter(reporter) }
  )
}

class FilteringReporter(reporter: xsbti.Reporter) extends xsbti.Reporter {

  def reset(): Unit = reporter.reset()
  def hasErrors: Boolean = reporter.hasErrors
  def hasWarnings: Boolean = reporter.hasWarnings
  def printSummary(): Unit = reporter.printSummary()
  def problems: Array[xsbti.Problem] = reporter.problems

  def log(problem: xsbti.Problem): Unit = {
    if (!problem.message.contains("An existential type that came from a Scala-2 classfile cannot be"))
      reporter.log(problem)
  }

  def comment(pos: xsbti.Position, msg: String): Unit =
    reporter.comment(pos, msg)

  override def toString = s"CollectingReporter($reporter)"
}
