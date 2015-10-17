package models

import play.api.libs.ws._
import play.api.libs.json._
import scala.concurrent.Future

case class QuestionData(id: Long, question: String, qtype: String, atype: String, answer: List[String], deprecated: Boolean)
case class CorporaData(name: String, description: String, questions: List[QuestionData], deprecated: Boolean)
case class CorpusData(name: String, description: String, deprecated: Boolean)
case class StatisticData (
    id: Long,
    question: Option[String], sender: Option[String], node: Option[String],
    details: StatisticDataDetails,
    times: Option[Map[String, Long]],
    steps: Option[Map[String, String]],
    answer: Option[String], ranking: Option[Double], finished: Option[Boolean], candidates: Option[List[(String, Double)]])
case class StatisticDataDetails( corpus: Option[String], id: Option[Long], time: Option[Long], details: Option[String] )

trait StatisticModel extends RestfulModel {
    import play.api.Play.current
    val host : String = current.configuration.getString("statistics.url").get
    val port : String = current.configuration.getString("statistics.port").get
}

object QuestionData extends StatisticModel {
  import play.api.Play.current
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val questionFormat = Json.format[QuestionData]

  def put(question : QuestionData, corpusName : String) : Unit = {
    val url = s"$host:$port/corpora/$corpusName/question/create"
    val request = WS.url(url).post(Json.toJson(question))
  }
}

object CorporaData extends StatisticModel {
  import play.api.Play.current
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val corporaFormat = Json.format[CorporaData]

  def get(name : String) : Future[CorporaData] = {
    val url = s"$host:$port/corpora/$name"
    val request = WS.url(url)
    val futureResult: Future[JsResult[CorporaData]] = WS.url(url).get.map {
      response => (response.json).validate[CorporaData]
    }

    resultMap(futureResult)
  }
}

object CorpusData extends StatisticModel {
  import play.api.Play.current
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val corpusFormat = Json.format[CorpusData]

  def get() : Future[List[CorpusData]] = {
    val url = s"$host:$port/corpora"
    val request = WS.url(url)

    val futureResult: Future[JsResult[List[CorpusData]]] = WS.url(url).get.map {
      response => (response.json).validate[List[CorpusData]]
    }

    resultMap(futureResult)
  }
}

object StatisticData extends StatisticModel {
  import play.api.Play.current
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit def tuple2Reads[A, B](implicit aReads: Reads[A], bReads: Reads[B]): Reads[Tuple2[A, B]] = Reads[Tuple2[A, B]] {
    case JsArray(arr) if arr.size == 2 => for {
      a <- aReads.reads(arr(0))
      b <- bReads.reads(arr(1))
    } yield (a, b)
    case _ => JsError(Seq(JsPath() -> Seq(play.api.data.validation.ValidationError("Expected array of two elements"))))
  }

  implicit val tupleFormat = tuple2Reads[String, Double]
  implicit val statsDetailsFormat = Json.reads[StatisticDataDetails]
  implicit val statsFormat = Json.reads[StatisticData]

  def getId(id: Long) : Future[StatisticData] = {
    val url = s"$host:$port/stats/bson/$id"
    //val request = WS.url(url)

    val futureResult: Future[JsResult[StatisticData]] = WS.url(url).get.map {
      response => (response.json).validate[StatisticData]
    }

    resultMap(futureResult)
  }

  def getList(count: Int) : Future[List[StatisticData]] = {
    val url = s"$host:$port/stats/bson/last/$count"
    //val request = WS.url(url)

    val futureResult: Future[JsResult[List[StatisticData]]] = WS.url(url).get.map {
      response => (response.json).validate[List[StatisticData]]
    }

    resultMap(futureResult)
  }
}
