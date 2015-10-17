package elementary.util.data

import reactivemongo.api._
import reactivemongo.bson._
import scala.util.{Try, Success, Failure}
import scala.concurrent.{Future, Promise}
import scalaz._, Scalaz._

//! Stores the data of a document in the db
case class ProcessDataDetails( corpus: Option[String], id: Option[Long], time: Option[Long], details: Option[String] ) {
  def +(that: ProcessDataDetails): ProcessDataDetails = {
    ProcessDataDetails(this.corpus.orElse(that.corpus), this.id.orElse(that.id), this.time.orElse(that.time), this.details.orElse(that.details))
  }
}
case class ProcessData (
  id: Long,
  question: Option[String], sender: Option[String], node: Option[String],
  details: ProcessDataDetails,
  times: Option[Map[String, Long]],
  steps: Option[Map[String, String]],
  answer: Option[String], ranking: Option[Double], finished: Option[Boolean], candidates: Option[List[(String, Double)]]) {
  // combine process data
  def +(that: ProcessData): ProcessData = {
    that match {
      case ProcessData(tid, tquestion, tsender, tnode, tdetails, ttimes, tsteps, tanswer, tranking, tfinished, tcandidates) if (tid==this.id) => {
        val time: Option[Map[String, Long]] = (this.times, ttimes) match {
          case (Some(t1), Some(t2)) => Some((t1.mapValues{List(_)} |+| t2.mapValues{List(_)}).mapValues(ls => ls.max))
          case _ => this.times.orElse(ttimes)
        }
        val step: Option[Map[String, String]] = (this.steps, tsteps) match {
          case (Some(s1), Some(s2)) => Some((s1.mapValues{List(_)} |+| s2.mapValues{List(_)}).mapValues(ls => ls.maxBy(_.length)))
          case _ => this.steps.orElse(tsteps)
        }
        ProcessData(this.id, this.question.orElse(tquestion), this.sender.orElse(tsender), this.node.orElse(tnode), this.details + tdetails,
          time, step, this.answer.orElse(tanswer), this.ranking.orElse(tranking), this.finished.orElse(tfinished),
          this.candidates.orElse(tcandidates))
      }
      case _ => this
    }
  }
}

// Companion object to provide access to the database
abstract trait ProcessDataBase extends MongoFacade {
  import scala.concurrent.ExecutionContext.Implicits.global

  // Define database names
  override val mongoDBName = "ElementaryStatistics"
  override val mongoCollName = "ProcessData"

  //! Parse a document into the case class
  implicit object DataReader extends BSONDocumentReader[ProcessData] {
    def read(doc: BSONDocument): ProcessData = {
      // load the details as seperate element
      val detailsDoc = doc.getAs[BSONDocument]("details")
      val details = ProcessDataDetails( detailsDoc.flatMap(_.getAs[String]("corpus")), detailsDoc.flatMap(_.getAs[Long]("id")),
        detailsDoc.flatMap(_.getAs[Long]("time")), detailsDoc.flatMap(_.getAs[String]("details")) )
      // read the maps of data
      def checkNull(x: Double): Double = if((x equals Double.NaN) || x.toString == "NaN" || x <= Double.MinValue || x >= Double.MaxValue) 0.0 else x
      val candidates = doc.getAs[List[BSONDocument]]("candidates").map(_.map( d =>
        ( d.getAs[String]("answer").getOrElse(""), checkNull(d.getAs[Double]("ranking").getOrElse(0.0)) )
      ))
      val times = doc.getAs[BSONDocument]("times").map(d =>
        d.elements.map( t => (t._1, d.getAs[Long](t._1).getOrElse(0L)) ).toMap
      )
      val steps = doc.getAs[BSONDocument]("steps").map(d =>
        d.elements.map( t => (t._1, d.getAs[String](t._1).getOrElse("")) ).toMap
      )
      // create the final document
      ProcessData( doc.getAs[Long]("id").getOrElse(0), doc.getAs[String]("question"), doc.getAs[String]("sender"), doc.getAs[String]("node"),
        details, times, steps, doc.getAs[String]("answer"), doc.getAs[Double]("ranking"), doc.getAs[Boolean]("finished"),
        candidates.map(_.sortWith(_._2 > _._2).take(10))
      )
    }
  }

  //! Checks if a document with the specific id already exists
  def exists(id: Long): Future[Boolean] = {
    val query = BSONDocument("id" -> id)
    mongo.find(query).one[BSONDocument].map( _ match { case Some(x) => true; case None => false } )
  }
}
