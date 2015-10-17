package elementary.statistic

import akka.actor.{ActorLogging, Actor}
import elementary.util.data.ProcessData
import elementary.statistic.analysis.ProcessAnalyzer
import scala.util.{Success, Failure}

case class AnalyzeDoc(id: Long)
case object ReAnalyze

//! Analyzes each new document in the dataset
class AnalysisActor extends Actor with ActorLogging {
  import scala.concurrent.ExecutionContext.Implicits.global

  def receive = {
    case AnalyzeDoc(id) =>
      ProcessAnalyzer.analyzeSingle(id) onComplete {
        case Failure(e) => log.error("Analysis of doc {} failed with exception {}", id, e)
        case Success(_) =>
      }
    case ReAnalyze =>
      ProcessAnalyzer.analyze() onComplete {
        case Failure(e) => log.error("Failed to re-analyze documents with exception {}", e)
        case Success(_) =>
      }
  }
}
