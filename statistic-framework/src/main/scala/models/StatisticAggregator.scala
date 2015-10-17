package elementary.statistic.models

import reactivemongo.api._
import reactivemongo.bson._
import elementary.util.data.MongoFacade
import scala.util.{Try, Success, Failure}
import scala.concurrent.{Future, Promise}

// TODO define case classes that store
case class Measure(precision: Double, recall: Double, f1: Double)
case class Hits(total: Int, completed: Int, correct: Int, candidates: Int, incorrect: Int)
case class QuestionTypeStatistics(kind: String)
case class CorpusStatistics(name: String, time: Long, hits: Hits, measure: Measure)
case class TimeStatistics(start: Long, end: Long)

// Reads statistic data from the statisticData collection
object StatisticAggregator extends MongoFacade {
  import scala.concurrent.ExecutionContext.Implicits.global

  // Define database names
  override val mongoDBName = "ElementaryStatistics"
  override val mongoCollName = "StatisticData"

  // retrieve the overall system stats for the given time window
  def timeStats(start: Long, end: Long): Future[Option[TimeStatistics]] = ???

  // retrieve the newest stats for the given corpus
  def corpusStats(name: String): Future[Option[CorpusStatistics]] = ???

  // retrieves the complete history for a specific corpus
  def corpusHistory(name: String): Future[Option[List[CorpusStatistics]]] = ???

  // retrieves the statistics for a specific question type for a specific time window
  def questionTypeStats(kind: String, start: Long, end: Long): Future[Option[QuestionTypeStatistics]] = ???

  // retrieves the statistics for a specific question type for a specific corpus run
  def questionTypeStats(kind: String, corpus: String, time: Long): Future[Option[QuestionTypeStatistics]] = ???
}
