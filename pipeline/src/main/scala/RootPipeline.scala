package elementary.pipeline

import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Source, Sink, FlowGraph, Broadcast, Merge, Flow, RunnableGraph, Keep}

import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import elementary.pipeline.analysis.AnalysisPipeline
import elementary.pipeline.extraction.ExtractionPipeline
import elementary.pipeline.ranking.RankingPipeline
import elementary.pipeline.answer.AnswerPipeline
import elementary.pipeline.candidates.CandidatePipeline


object RootPipeline {
  val reporter = StatisticsActor.get

  // init databases (might make optional)
  elementary.util.data.Transcript.client

  val flow = Flow() { implicit b =>
    import FlowGraph.Implicits._

    val bcast = b.add(Broadcast[QueryRef](1))
    val merge = b.add(Merge[AnswerRef](1))
    val reportFlow = Flow[QueryRef].map(q => {
      reporter ! ProcessReportMessage(q.id, "insert", pipeline.build.BuildInfo.toJson, System.currentTimeMillis)
      q
    })

    bcast ~> reportFlow ~> AnalysisPipeline.flow ~> ExtractionPipeline.flow ~>
      CandidatePipeline.flow ~> RankingPipeline.flow ~> AnswerPipeline.flow ~> merge

    (bcast.in, merge.out)
  }
}
