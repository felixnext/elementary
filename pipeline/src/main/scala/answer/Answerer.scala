package elementary.pipeline.answer

import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import scala.concurrent.Future
import com.typesafe.config.ConfigFactory

object Answerer {
  import scala.concurrent.ExecutionContext.Implicits.global

  val threshold = {
    val config = ConfigFactory.load()
    config.getDouble("elementary.parameter.answer.threshold")
  }

  def convert(candidates: Future[List[ResultData]]): Future[Option[AnswerData]] = {
    candidates.map(ls => {
      val best = ls.maxBy(res => res.ranking)
      if (best.ranking > threshold) {
        Some(AnswerData(best.candidate, List(), best.ranking))
      }
      else None
    })
  }
}
