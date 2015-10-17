package elementary.pipeline.candidates

import akka.stream.scaladsl.{Flow}
import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import elementary.util.data.Transcript
import elementary.util.machinelearning.MLTools
import elementary.util.machinelearning.TextExtraction._
import scala.concurrent.Future

trait Synthesizer {
  // flow component that connects the graph
  val flow: Flow[ExtractionRef, CandidateRef, Unit]

  // returns the config of the item
  val configuration: Map[String, String]
}

// Extracts candidates from the documents
object Synthesizer {
  import scala.concurrent.ExecutionContext.Implicits.global

  val tools = new MLTools()

  def metricExtraction(extraction: ExtractionRef): Future[List[CandidateData]] = {
    // TODO extract data from documents
    extraction.docs.map(docs => docs.flatMap( doc => {
      val extract = tools.metricExtraction(doc.doc.text, 0.4, extraction.data.bow)
      extract.map( tpl => CandidateData(tpl._1, tpl._2, tpl._3, doc.doc.id) )
    }))
  }
}
