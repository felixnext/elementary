package elementary.util.common

import akka.actor.ActorRef
import elementary.util.data.Transcript
import scala.concurrent.Future

// Holds all messages that are used to communicate between different modules
object Communication {
  // Messages for the statistic framework
  abstract trait ReportMessage { def id: Long }
  case class BasicReportMessage(id: Long, json: String, time: Long) extends ReportMessage
  case class QuestionReportMessage(id: Long, question: String, sender: String, json: String, time: Long) extends ReportMessage
  case class PipelineReportMessage(id: Long, node: String, time: Long) extends ReportMessage
  case class ProcessReportMessage(id: Long, step: String, details: String, time: Long) extends ReportMessage
  case class AnswerReportMessage(id: Long, success: Boolean, answer: String, ranking: Double,
    candidates: List[ResultData], time: Long) extends ReportMessage
  case class ReportReceived(id: Long)

  // Messages to communicate with the cluster
  case object BackendRegistration
  case object PipelineShutdown

  // Messages between the pipeline modules
  trait PipelineMessage

  final case class ResultData(candidate: String, topic: String, lecture: String, resources: List[String], ranking: Double)

  sealed trait QuestionMessage extends PipelineMessage
  final case class QuestionRequest(query: String, sender: String, details: String) extends QuestionMessage
  final case class Question(id: Long, query: String) extends QuestionMessage

  abstract trait AnswerMessage extends PipelineMessage { def id: Long }
  final case class QuestionDenied(id: Long)         extends AnswerMessage // question will be denied if input buffer is full
  final case class Answer(id: Long, answer: String) extends AnswerMessage
  final case class AnswerUnkown(id: Long)           extends AnswerMessage // System could not find a sufficent answer
  final case class NoAnswer(id: Long)               extends AnswerMessage // Unable to retrieve an answer
}
