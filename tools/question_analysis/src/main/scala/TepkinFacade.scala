package elementary.tools.answerscrawler

import net.fehmicansaglam.bson.BsonDocument
import net.fehmicansaglam.bson.BsonDsl._
import net.fehmicansaglam.bson.Implicits._
import net.fehmicansaglam.tepkin.MongoClient
import akka.util.Timeout

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

import akka.stream.scaladsl._

trait TepkinFacade {
  implicit val timeout: Timeout = 5.seconds

  val client = MongoClient("mongodb://is62.idb.cs.tu-bs.de:27017")
  val db = client("YahooAnswers")
  val collection = db("Questions")
  val query: BsonDocument = BsonDocument.empty
  val fields: BsonDocument = $document(
      "id" := 1,
      "question" := 1,
      "category" := 1,
      "taken" := 1
    )

  val srcMongoList = collection.find(query, Some(fields), 0, false, 10)
  val srcMongo = srcMongoList.mapConcat(identity)

  def updateQuestion(q: Question, taken: Boolean): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    Await.result(collection.findAndUpdate(
      query = Some("id" := q.id),
      update = $set("taken" := taken),
      returnNew = true
    ), 5.seconds)
  }
}
