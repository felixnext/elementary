package elementary.pipeline.ranking

import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{FlowGraph, Broadcast, Flow, ZipWith, Merge}
import com.typesafe.config.ConfigFactory
import elementary.pipeline.StatisticsActor
import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import scala.concurrent.Future
import scala.util.{Success, Failure}

//! Defines the pipeline for the question analsis module
object RankingPipeline {
  import scala.concurrent.ExecutionContext.Implicits.global

  // NOTE: USE Answer-entailment in order to check answer candidates as hypothesis
  // NOTE: also split to many parallel ranking algorithms (merge approach?)

  // create an actor to report to stats framework
  val reporter = StatisticsActor.get
  val cfg = "elementary.parameter.ranking"

  lazy val flow = Flow() { implicit b =>
    import FlowGraph.Implicits._

    // define the connecting elements
    val bcast = b.add(Broadcast[CandidateRef](1))
    val merge = b.add(Merge[RankingRef](1))

    // load the ranker
    val config = ConfigFactory.load()
    val rnk: Ranker = config.getString(s"$cfg.mode") match {
      case "passing" | _ => new PassingRanker()
    }
    val configStr = rnk.configuration.foldLeft("")((o, n) => s"""$o,"${n._1}": "${n._2}" """).substring(1)

    val actorFlow = Flow[RankingRef].map(q => {
      reporter ! ProcessReportMessage(q.id, "ranking_started", s"""{$configStr}""", System.currentTimeMillis)
      q.candidates.onComplete {
        case Success(cands) => reporter ! ProcessReportMessage(q.id, "ranking_done",
          s"""{"cands": ${cands.size}, "answer": "${if(cands.size>0) cands.head else "[NONE]"}"}""", System.currentTimeMillis)
        case Failure(e) => reporter ! ProcessReportMessage(q.id, "ranking_done", s"""{"failed": $e}""", System.currentTimeMillis)
      }
      q
    })

    // construct the actual flow
    bcast ~> rnk.flow ~> actorFlow ~> merge

    (bcast.in, merge.out)
  }
}
