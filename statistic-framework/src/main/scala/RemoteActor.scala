package elementary.statistic

import akka.actor.{ActorLogging, Actor}
import elementary.util.data.ProcessDataCreator
import elementary.util.common.Communication.{BasicReportMessage, QuestionReportMessage,
  PipelineReportMessage, ProcessReportMessage, AnswerReportMessage, ReportReceived}
import scala.util.{Success, Failure}

//! Actor that receives and stores data from the main system
class RemoteActor extends Actor with ActorLogging {
  import scala.concurrent.ExecutionContext.Implicits.global

  // TODO get reference to the remote actor
  ProcessDataCreator.mongoClient

  def receive = {
    case BasicReportMessage(id, json, time) =>
      log.error("Received BasicReportMessage with id {}", id)
      sender ! ReportReceived(id)
    case QuestionReportMessage(id, question, tasker, json, time) =>
      log.info("Received QuestionReportMessage with id {} from {}", id, tasker)
      sender ! ReportReceived(id)
      ProcessDataCreator.appendCreate(id, question, tasker, json, time) onComplete {
        case Failure(e) => log.error("Question creation for id {} failed with {}", id, e)
        case Success(false) => log.error("Could not create new question document for id {}", id)
        case _ =>
      }
    case PipelineReportMessage(id, node, time) =>
      log.info("Received PipelineReportMessage with id {}", id)
      sender ! ReportReceived(id)
      ProcessDataCreator.appendCluster(id, node, time) onComplete {
        case Failure(e) => log.error("Cluster insert for id {} failed with {}", id, e)
        case Success(false) => log.error("Could not insert cluster data for id {}", id)
        case _ =>
      }
    case ProcessReportMessage(id, step, details, time) =>
      log.info("Received ProcessReportMessage with id {}", id)
      sender ! ReportReceived(id)
      ProcessDataCreator.appendProcess(id, step, details, time) onComplete {
        case Failure(e) => log.error("Process Data insert for id {} failed with {}", id, e)
        case Success(false) => log.error("Could not insert process data for id {}", id)
        case _ =>
      }
    case AnswerReportMessage(id, success, answer, ranking, candidates, time) =>
      log.info("Received AnswerReportMessage with id {}", id)
      sender ! ReportReceived(id)
      ProcessDataCreator.appendFinish(id, success, answer, ranking, candidates.map(cand => (cand.candidate, cand.ranking)), time) onComplete {
        case Failure(e) => log.error("Final information insert for id {} failed with {}", id, e)
        case Success(false) => log.error("Could not complete question document for id {}", id)
        case _ =>
      }
    case msg =>
      log.error("Received unkown message '{}'", msg)
  }
}
