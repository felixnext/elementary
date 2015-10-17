package elementary.pipeline.ranking

import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{FlowGraph, Broadcast, Flow, Merge, ZipWith}
import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import scala.concurrent.Future

// defines the abstract trait of a ranker
trait Ranker {
  val flow: Flow[CandidateRef, RankingRef, Unit]

  // returns the config of the item
  val configuration: Map[String, String]
}

// Ranks data based on additional informations
object Ranker {
  import scala.concurrent.ExecutionContext.Implicits.global

  def naiveRank(futCand: Future[List[CandidateData]], futDocs: Future[List[ExtractionData]]): Future[List[ResultData]] = {
    futCand.zip(futDocs).map(tpl => {
      val docs = tpl._2.map(doc => (doc.doc.id, doc)).toMap
      tpl._1.map( cand => {
        val doc = docs(cand.docID)
        ResultData(cand.candidate, doc.doc.module, doc.doc.lectype, List(), cand.ranking)
      })
    })
  }
}

// defines a simple ranker that only selects the highest currently ranked element
class PassingRanker extends Ranker {
  import scala.concurrent.ExecutionContext.Implicits.global

  val configuration: Map[String, String] = Map("name" -> "passing")

  // defines the actual extraction for the baseline
  lazy val flow = Flow() { implicit b =>
    import FlowGraph.Implicits._

    // define the start and endpoints
    val bcast = b.add(Broadcast[CandidateRef](1))
    val merge = b.add(Merge[RankingRef](1))

    // define the flow to work with
    val transform = Flow[CandidateRef].map(cand => RankingRef(cand.id, cand.data,
      cand.candidates.map(_.map(item => ResultData(item.candidate, "", "", List(), item.ranking)).sortWith(_.ranking > _.ranking)), cand.sender))

    // outline the actual flow
    bcast ~> transform ~> merge

    (bcast.in, merge.out)
  }
}
