package elementary.pipeline.candidates

import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{FlowGraph, Broadcast, Flow, ZipWith, Merge}
import com.typesafe.config.ConfigFactory
import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import elementary.util.data._
import scala.concurrent.Future
import scala.util.{Success, Failure, Try}

class BaselineSynthesizer(bow: Boolean = true, focus: Boolean = false) extends Synthesizer {
  import scala.concurrent.ExecutionContext.Implicits.global

  // load the stopwords
  val config = ConfigFactory.load()
  val stopwords: List[String] = Try(
    scala.io.Source.fromFile(config.getString("elementary.parameter.general.stopwords")).getLines().toList
  ).toOption.getOrElse(List())

  // holds the configuration
  val configuration: Map[String, String] = Map("name" -> "baseline", "bow" -> bow.toString, "focuswords" -> focus.toString)

  // defines the flow for the baseline candidate system
  lazy val flow = Flow() { implicit b =>
    import FlowGraph.Implicits._

    // define the start and endpoints
    val bcast = b.add(Broadcast[ExtractionRef](2))
    val zipping = b.add(ZipWith[ExtractionRef, Future[List[CandidateData]], CandidateRef] {
      (ref, cands) =>
        CandidateRef(ref.id, ref.data, ref.docs, cands, ref.sender)
    })

    // define the flow to work with
    val passThrough = Flow[ExtractionRef]
    val findWords   =
      if (!focus) Flow[ExtractionRef].map(ref => ref.data.bow.filter(!stopwords.contains(_)))
      else        Flow[ExtractionRef].map(ref => ref.data.focus)

    // check if relevant words have been found (if not do not extract -> no answer found)
    def findTerms(words: List[String]): Future[List[(Transcript, Double)]] = {
      if (words.size > 0) Transcript.findTerms(words, Snippets)
      else Future(List())
    }
    // convert the serch into a flow object
    val synthesize  = Flow[List[String]].map(words => findTerms(words).map(_.map(tpl =>
      CandidateData(s"[id: ${tpl._1.id}, corpus: ${tpl._1.corpus}, department: ${tpl._1.department}, module: ${tpl._1.module}]\n${tpl._1.text}",
        tpl._2, tpl._1.offset, tpl._1.id)
    )))

    // outline the actual flow
    bcast ~> passThrough             ~> zipping.in0
    bcast ~> findWords ~> synthesize ~> zipping.in1

    (bcast.in, zipping.out)
  }
}
