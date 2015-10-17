package elementary.statistic.analysis

import scala.concurrent.Future
import elementary.util.data.ProcessData

object ProcessAnalyzer {
  import scala.concurrent.ExecutionContext.Implicits.global

  // analyze a single document from the mongo db
  def analyzeSingle(id: Long): Future[Unit] = {
    Future.failed(???)
  }

  // drops old data an re-analyzes all data from the mongo db
  def analyze(): Future[Unit] = {
    Future.failed(???)
  }
}
