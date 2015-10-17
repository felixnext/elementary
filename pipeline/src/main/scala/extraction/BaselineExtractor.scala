package elementary.pipeline.extraction

import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{FlowGraph, Broadcast, Flow, Merge, ZipWith}
import com.typesafe.config.ConfigFactory
import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import elementary.util.data.Transcript
import scala.concurrent.Future

class BaselineExtractor(extend: Boolean = false) extends Extractor {
  import scala.concurrent.ExecutionContext.Implicits.global

  val configuration: Map[String, String] = Map("name" -> "baseline", "extendQuery" -> extend.toString)

  // calculates the bag of words and extends the query
  def bagOfWordsExtended(data: QuestionData): List[String] = {
    // TODO implement
    data.bow
  }

  // extracts documents from lucene
  def searchDocs(words: List[String]): Future[List[(Transcript, Double)]] = {
    Transcript.findTerms(words)
  }

  // defines the actual extraction for the baseline
  lazy val flow = Flow() { implicit b =>
    import FlowGraph.Implicits._

    // define the start and endpoints
    val bcast = b.add(Broadcast[QuestionRef](2))
    val zipping = b.add(ZipWith[QuestionRef, Future[List[ExtractionData]], ExtractionRef] {
      (q, docs) =>
        ExtractionRef(q.id, q.data, docs, q.sender)
    })

    // define the flow to work with
    val passThrough = Flow[QuestionRef]
    val bowFlow = if (extend) Flow[QuestionRef].map(q => bagOfWordsExtended(q.data)) else Flow[QuestionRef].map(q => q.data.bow)
    val docFlow = Flow[List[String]].map(ls => searchDocs(ls).map(_.map(tpl => ExtractionData(tpl._1, tpl._2, List()))))

    // outline the actual flow
    bcast ~> passThrough        ~> zipping.in0
    bcast ~> bowFlow ~> docFlow ~> zipping.in1

    (bcast.in, zipping.out)
  }
}
