package elementary.util.data

import reactivemongo.api._
import reactivemongo.bson._
import scala.util.{Try, Success, Failure}
import scala.concurrent.{Future, Promise}

// Companion object to provide access to the database
object ProcessDataCreator extends ProcessDataBase {
  import scala.concurrent.ExecutionContext.Implicits.global

  //! Creates the doc with the single id
  def createDoc(id: Long): Future[Boolean] = {
    // create a promise to be used thorugh the futures
    val promise = Promise[Boolean]()
    // wait for the exist check to complete
    exists(id).onComplete {
      case Success(false) =>
        // actually create the doc
        val doc = BSONDocument("id" -> id)
        mongo.insert(doc).onComplete {
          case Success(data) =>
            promise.success(data.ok)
          case Failure(e) => promise.failure(e)
        }
      case Success(true) => promise.success(false)
      case Failure(e) => promise.failure(e)
    }
    // return the future handle
    promise.future
  }

  def handleUpdate(id: Long, modifier: BSONDocument): Future[Boolean] = {
    val selector = BSONDocument("id" -> id)
    val promise = Promise[Boolean]()
    createDoc(id).onComplete {
      case _ =>
        mongo.update(selector, modifier, upsert=true).onComplete {
          case Success(data) =>
            promise.success(data.ok && data.updated > 0)
          case Failure(e) => promise.failure(e)
        }
    }

    promise.future
  }

  //! Creates a new document for the given question
  def appendCreate(id: Long, question: String, sender: String, details: String, time: Long): Future[Boolean] = {
    import spray.json._
    import DefaultJsonProtocol._

    // monadic functions to capture side effects from parsing
    def parseString(obj: JsValue, field: String): Option[String] = {
      Try( obj.asJsObject.getFields(field) match {
        case Seq(JsString(value)) => Some(value)
        case _ => None
      } ).toOption.flatten
    }
    def parseLong(obj: JsValue, field: String): Option[Long] = {
      Try( obj.asJsObject.getFields(field) match {
        case Seq(JsNumber(value)) => Some(value.toLong)
        case _ => None
      } ).toOption.flatten
    }

    // try to get a json and push it into a bson doc
    val doc = Try(details.parseJson).toOption match {
      case Some(json) =>
        BSONDocument(
          "question" -> question, "sender" -> sender,
          "details.corpus" -> parseString(json, "corpus").getOrElse(""),
          "details.time" -> parseLong(json, "time").getOrElse(0L),
          "details.id" -> parseLong(json, "id").getOrElse(0L),
          "details.details" -> parseString(json, "details").getOrElse(""),
          "times.create" -> time
        )
      case None => BSONDocument("question" -> question, "sender" -> sender, "times.create" -> time)
    }
    // create the update document and execute it
    val modifier = BSONDocument( "$set" -> doc )
    handleUpdate(id, modifier)
  }

  //! Appends information about the cluster to the question document
  def appendCluster(id: Long, node: String, time: Long): Future[Boolean] = {
    val modifier = BSONDocument(
      "$set" -> BSONDocument(
        "node" -> node,
        "times.node" -> time
      )
    )

    handleUpdate(id, modifier)
  }

  //! Appends information for a single process step onto the document
  def appendProcess(id: Long, step: String, json: String, time: Long): Future[Boolean] = {
    val modifier = BSONDocument(
      "$set" -> BSONDocument(
        s"steps.$step" -> json,
        s"times.$step" -> time
      )
    )

    handleUpdate(id, modifier)
  }

  //! Complete the question document by adding informations about the answers
  def appendFinish(id: Long, success: Boolean, answer: String, ranking: Double, candidates: List[(String, Double)], time: Long): Future[Boolean] = {
    val modifier = BSONDocument(
      "$set" -> BSONDocument(
        "answer" -> answer, "ranking" -> ranking, "finished" -> success,
        "times.finish" -> time,
        "candidates" -> candidates.map( t => BSONDocument("answer" -> t._1, "ranking" -> t._2) )
      )
    )

    handleUpdate(id, modifier)
  }
}
