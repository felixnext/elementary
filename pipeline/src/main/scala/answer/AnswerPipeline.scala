package elementary.pipeline.answer

import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{FlowGraph, Broadcast, Flow, ZipWith, Merge}
import elementary.pipeline.StatisticsActor
import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import scala.concurrent.Future

import elementary.util.common.Communication._

//! Defines the pipeline for the question analsis module
object AnswerPipeline {
  import scala.concurrent.ExecutionContext.Implicits.global

  // create an actor to report to stats framework
  val reporter = StatisticsActor.get

  lazy val flow = Flow() { implicit b =>
    import FlowGraph.Implicits._

    val bcast = b.add(Broadcast[RankingRef](2))
    val zipping = b.add(ZipWith[RankingRef, Future[Option[AnswerData]], AnswerRef]{
      (ref, answer) =>
        AnswerRef(ref.id, ref.data, answer, ref.candidates, ref.sender)
    })
    val merge = b.add(Merge[AnswerRef](1))
    val passThrough = Flow[RankingRef]
    val answerFlow = Flow[RankingRef].map(q => Answerer.convert(q.candidates))

    val actorFlow = Flow[AnswerRef].map(q => {
      reporter ! ProcessReportMessage(q.id, "answer", """{}""", System.currentTimeMillis)
      q
    })

    bcast ~> passThrough    ~> zipping.in0
    bcast ~> answerFlow     ~> zipping.in1
                               zipping.out ~> actorFlow ~> merge

    (bcast.in, merge.out)
  }
}
