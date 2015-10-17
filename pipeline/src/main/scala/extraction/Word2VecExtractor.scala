package elementary.pipeline.extraction

import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{FlowGraph, Broadcast, Flow, Merge, ZipWith}
import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import elementary.util.data.Transcript
import scala.concurrent.Future

class Word2VecExtractor(focusWord: Boolean = false) extends Extractor {
  import scala.concurrent.ExecutionContext.Implicits.global

  val configuration: Map[String, String] = Map("name" -> "Word2Vec", "focusWord" -> focusWord.toString)

  // load transcript vectors
  val vectors = Transcript.vectors(elementary.util.data.Transcripts)

  // extracts documents from lucene
  def searchDocs(data: QuestionData): Future[List[(Transcript, Double)]] = {
    Future(List())
  }

  lazy val flow = Flow() { implicit b =>
    import FlowGraph.Implicits._

    val bcast = b.add(Broadcast[QuestionRef](2))
    val zipping = b.add(ZipWith[QuestionRef, Future[List[ExtractionData]], ExtractionRef] {
      (q, docs) =>
        ExtractionRef(q.id, q.data, docs, q.sender)
    })
    val passThrough = Flow[QuestionRef]
    val docFlow = Flow[QuestionRef].map(q => Extractor.searchDocs(q.data)).map(docs => Extractor.enrichDocs(docs))

    bcast ~> passThrough ~> zipping.in0
    bcast ~> docFlow     ~> zipping.in1

    (bcast.in, zipping.out)
  }
}
