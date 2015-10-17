package elementary.util.data

import reactivemongo.api._
import reactivemongo.bson._
import scala.util.{Try, Success, Failure}
import scala.concurrent.{Future, Promise}

// Companion object to provide access to the database
object ProcessDataMerger extends ProcessDataBase {
  import scala.concurrent.ExecutionContext.Implicits.global

  //! Lists all documents in the db
  def list(): Future[List[ProcessData]] = {
    mongo.
      find(BSONDocument()).
      cursor[ProcessData].
      collect[List]()
  }

  //! Retrieves a specific document from the db
  def get(id: Long): Future[Option[ProcessData]] = {
    val query = BSONDocument("id" -> id)
    val futLs = mongo.find(query).cursor[ProcessData].collect[List]()
    futLs.map(ls => ls match {
      case head :: Nil => Some(head)
      case head :: tail => Some(ls.tail.foldLeft(ls.head)(_ + _))
      case Nil => None
    })
  }

  // Gets the latest element
  def latest: Future[Option[ProcessData]] = {
    val futOpt = mongo.find(BSONDocument()).sort(BSONDocument("id" -> -1)).one[ProcessData]
    futOpt.flatMap(opt => opt match {
      case Some(data) => get(data.id)
      case None => Future(None)
    })
  }

  // gets the last x elements
  def newest(count: Int): Future[List[ProcessData]] = {
    import reactivemongo.core.commands._
    // TODO fix this code!
    val pipeline: Seq[ PipelineOperator ] = Seq(
      Group( BSONString( "$id" ) )( ),
      Sort(Seq(Descending("id"))),
      Limit(count * 40)
    )
    val command = Aggregate( "ProcessData", pipeline )
    val futOpt = mongoDB.command(command)
    futOpt.flatMap(res => {
      val ids = res.toList.map(_ match {
        case doc: BSONDocument => doc.get("_id").flatMap(_ match { case BSONLong(id) => Some(id); case _ => None})
        case _ => None
      }).filter(!_.isEmpty).map(_.get).sortBy(-_).take(count)
      (Future sequence ids.map(id => get(id))).map(_.filter(!_.isEmpty).map(_.get))
    })
  }

  //! Counts all documents in the db
  def count: Future[Long] = {
    mongo.find(BSONDocument()).cursor[ProcessData].collect[List]().map(ls => ls.size)
  }
}
