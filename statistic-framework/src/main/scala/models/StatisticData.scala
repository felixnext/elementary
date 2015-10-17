package elementary.statistic.models

import reactivemongo.api._
import reactivemongo.bson._
import elementary.util.data.MongoFacade
import scala.util.{Try, Success, Failure}
import scala.concurrent.{Future, Promise}

// Writes statistic data from the statisticData collection
object StatisticData extends MongoFacade {
  import scala.concurrent.ExecutionContext.Implicits.global

  // Define database names
  override val mongoDBName = "ElementaryStatistics"
  override val mongoCollName = "StatisticData"

  case class DocExists(exist: Boolean, create: Boolean, node: Boolean, finish: Boolean)

  //! Checks if a document with the specific id already exists
  def exists(id: Long): Future[Boolean] = {
    val query = BSONDocument("id" -> id)
    mongo.find(query).one[BSONDocument].map( _ match { case Some(x) => true; case None => false } )
  }

  //! Creates the doc with the single id
  def createDoc(id: Long): Future[Boolean] = {
    // create a promise to be used thorugh the futures
    val promise = Promise[Boolean]()
    // wait for the exist check to complete
    exists(id).onComplete {
      case Success(false) =>  // Note: Docuemnt does not exist
        val doc = BSONDocument("id" -> id)
        mongo.insert(doc).onComplete {
          case Success(data) => promise.success(data.ok)
          case Failure(e)    => promise.failure(e)
        }
      case Success(true) => promise.success(false)
      case Failure(e)    => promise.failure(e)
    }
    // return the future handle
    promise.future
  }

  def insertCreate(id: Long, corpus: String, qid: Long, corpus_time: Long): Future[Boolean] = ???

  def insertNode(id: Long): Future[Boolean] = ???

  def insertFinish(id: Long): Future[Boolean] = ???

  def insertDB(id: Long): Future[Boolean] = ???
}
