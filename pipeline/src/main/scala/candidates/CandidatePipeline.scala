package elementary.pipeline.candidates

import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{FlowGraph, Broadcast, Flow, ZipWith, Merge}
import com.typesafe.config.ConfigFactory
import elementary.pipeline.StatisticsActor
import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import scala.concurrent.Future
import scala.util.{Success, Failure}

//! Defines the pipeline for the question analsis module
object CandidatePipeline {
  import scala.concurrent.ExecutionContext.Implicits.global

  // NOTE: split to text comprehension sub-tasks?

  // create an actor to report to stats framework
  val reporter = StatisticsActor.get
  val cfg = "elementary.parameter.candidates"
  val cfggen = "elementary.parameter.general"

  // lazy
  val flow = Flow() { implicit b =>
    import FlowGraph.Implicits._

    // create the connecting parts of the graph
    val bcast = b.add(Broadcast[ExtractionRef](1))
    val merge = b.add(Merge[CandidateRef](1))

    // load the Synthesizer implementation
    val config = ConfigFactory.load()
    val syn: Either[Throwable, Synthesizer] = config.getString(s"$cfg.mode") match {
      case "w2v"           => Word2VecSynthesizer.create(
        config.getString(s"$cfggen.dsm-folder") + config.getString(s"$cfggen.ds-model"),
        config.getBoolean(s"$cfg.focus-word")
      )
      case "baseline" | _  => Right(new BaselineSynthesizer(config.getBoolean(s"$cfg.bow"), config.getBoolean(s"$cfg.focus-word")))
    }
    val configStr = syn match {
      case Right(s) => s.configuration.foldLeft("")((o, n) => s"""$o,"${n._1}": "${n._2}" """).substring(1)
      case Left(e)  => s""" "error": "$e" """
    }

    // create the actor message system
    val actorFlow = Flow[CandidateRef].map(q => {
      reporter ! ProcessReportMessage(q.id, "candidates_started", s"""{$configStr}""", System.currentTimeMillis)
      q.candidates.onComplete {
        case Success(cands) => reporter ! ProcessReportMessage(q.id, "candidates_done",
          s"""{"candidates": ${cands.size}}""", System.currentTimeMillis)
        case Failure(e) => reporter ! ProcessReportMessage(q.id, "candidates_done", s"""{"failed": $e}""", System.currentTimeMillis)
      }
      q
    })

    // create the flow (simple passing if could not load)
    val flow = syn match {
      case Right(s) => s.flow
      case Left(_)  => Flow[ExtractionRef].map( ref => CandidateRef(ref.id, ref.data, ref.docs, Future(List()), ref.sender) )
    }

    bcast ~> flow ~> actorFlow ~> merge

    (bcast.in, merge.out)
  }
}
