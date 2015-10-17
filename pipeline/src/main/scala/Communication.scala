package elementary.pipeline

import akka.actor.ActorRef
import elementary.util.machinelearning.structures._
import elementary.util.data.Transcript
import scala.concurrent.Future
import elementary.util.common.Communication._

object PipeCommunication {
  sealed class ReferenceMessage(sender: ActorRef) extends PipelineMessage
  final case class QueryRef(id: Long, query: String, sender: ActorRef) extends ReferenceMessage(sender)

  final case class QuestionData(query: String, bow: List[String], qtype: QuestionType, atype: AnswerType,
    topic: List[String], focus: List[String], pos: List[PosLabel], ner: Map[String, String])
  final case class QuestionRef(id: Long, data: QuestionData, sender: ActorRef)
    extends ReferenceMessage(sender)

  sealed trait ExtendedDataLink
  final case class ExtractionData(doc: Transcript, score: Double, data: List[ExtendedDataLink])
  final case class ExtractionRef(id: Long, data: QuestionData, docs: Future[List[ExtractionData]], sender: ActorRef)
    extends ReferenceMessage(sender)

  final case class CandidateData(candidate: String, ranking: Double, offset: Int, docID: String)
  final case class CandidateRef(id: Long, data: QuestionData, docs: Future[List[ExtractionData]],
    candidates: Future[List[CandidateData]], sender: ActorRef) extends ReferenceMessage(sender)

  final case class RankingRef(id: Long, data: QuestionData, candidates: Future[List[ResultData]], sender: ActorRef)
    extends ReferenceMessage(sender)

  final case class AnswerData(answer: String, resources: List[String], ranking: Double)
  final case class AnswerRef(id: Long, data: QuestionData, answer: Future[Option[AnswerData]], candidates: Future[List[ResultData]], sender: ActorRef)
    extends ReferenceMessage(sender)
}
