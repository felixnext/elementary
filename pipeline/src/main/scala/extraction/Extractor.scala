package elementary.pipeline.extraction

import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{FlowGraph, Broadcast, Flow, Merge, ZipWith}
import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import elementary.util.data.Transcript
import scala.concurrent.Future

// Basic trait for document extractors
trait Extractor {
  // defines the flow that is defined through the implementation
  val flow: Flow[QuestionRef, ExtractionRef, Unit]

  // returns the config of the item
  val configuration: Map[String, String]
}

object Extractor {
  import scala.concurrent.ExecutionContext.Implicits.global

  def searchDocs(data: QuestionData): Future[List[(Transcript, Double)]] = {
    val fut = Transcript.find(data.focus, data.ner.map(tpl => tpl._2).toList, data.topic)
    fut.map(docs => rankTopics(docs))
  }

  def rankTopics(data: List[(Transcript, Double)]): List[(Transcript, Double)] = {
    data
  }

  def enrichDocs(docs: Future[List[(Transcript, Double)]]): Future[List[ExtractionData]] = {
    docs.map( _.map( tpl => ExtractionData(tpl._1, tpl._2, List()) ) )
  }
}

// defines an extrator to skip the actual extraction phase
class PassingExtractor extends Extractor {
  import scala.concurrent.ExecutionContext.Implicits.global

  val configuration = Map("name" -> "passing")

  // defines the flow that is executed in the main pipeline
  lazy val flow = Flow() { implicit b =>
    import FlowGraph.Implicits._

    // define the start and endpoints
    val bcast = b.add(Broadcast[QuestionRef](1))
    val merge = b.add(Merge[ExtractionRef](1))

    // define the flow to work with
    val transform = Flow[QuestionRef].map(ref => ExtractionRef(ref.id, ref.data, Future(List()), ref.sender))

    // outline the actual flow
    bcast ~> transform ~> merge

    (bcast.in, merge.out)
  }
}
