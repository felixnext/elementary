package elementary.pipeline.extraction

import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{FlowGraph, Broadcast, Flow, Merge, ZipWith}
import com.typesafe.config.ConfigFactory
import elementary.pipeline.StatisticsActor
import elementary.util.data.Transcript
import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import scala.concurrent.Future
import scala.util.{Success, Failure}

//! Defines the pipeline for the question analsis module
object ExtractionPipeline {
  import scala.concurrent.ExecutionContext.Implicits.global

  // create an actor to report to stats framework
  val reporter = StatisticsActor.get
  val cfg = "elementary.parameter.extraction"

  lazy val flow = Flow() { implicit b =>
    import FlowGraph.Implicits._

    // defines the extractor that is used for this implementation
    val config = ConfigFactory.load()
    val ext: Extractor = config.getString(s"$cfg.mode") match {
      case "fulltext"    => new BaselineExtractor(config.getBoolean(s"$cfg.extend-fulltext"))
      case "w2v-doc"     => new Word2VecExtractor(config.getBoolean(s"$cfg.focus-word"))
      case "passing" | _ => new PassingExtractor()
    }
    val configStr = ext.configuration.foldLeft("")((o, n) => s"""$o,"${n._1}": "${n._2}" """).substring(1)

    // configure graph components
    val bcast = b.add(Broadcast[QuestionRef](1))
    val merge = b.add(Merge[ExtractionRef](1))
    val actorFlow = Flow[ExtractionRef].map(q => {
      reporter ! ProcessReportMessage(q.id, "extraction_started", s"""{$configStr}""", System.currentTimeMillis)
      q.docs.onComplete {
        case Success(docs) => reporter ! ProcessReportMessage(q.id, "extraction_done", s"""{"docs": ${docs.size}}""", System.currentTimeMillis)
        case Failure(e) => reporter ! ProcessReportMessage(q.id, "extraction_done", s"""{"failed": $e}""", System.currentTimeMillis)
      }
      q
    })

    // outline the actual flow
    bcast ~> ext.flow ~> actorFlow ~> merge

    (bcast.in, merge.out)
  }
}
