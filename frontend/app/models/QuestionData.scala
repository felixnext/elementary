package models

import play.api.libs.ws._
import play.api.libs.json._
import scala.concurrent.Future

case class AnswerData(id: Long, answer: String)

trait ElementaryModel extends RestfulModel {
    import play.api.Play.current
    val host : String = current.configuration.getString("elementary.url").get
    val port : String = current.configuration.getString("elementary.port").get
}

object AskData extends ElementaryModel {
  import play.api.Play.current
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val corporaFormat = Json.format[AnswerData]

  def get(question : String) : Future[AnswerData] = {
    val url = s"$host:$port/ask"
    val json = s"""{"question":"$question","details":"{}"}"""
    val futureResult: Future[AnswerData] = WS.url(url).withHeaders("Content-Type" -> "application/json").post(json).map {
      response => response.status match {
        case 200 if ((response.json \ "type").asOpt[String] == Some("error")) => AnswerData((response.json \ "id").as[Long], "[" + (response.json \ "msg").as[String] + "]")
        case 200 => (response.json).validate[AnswerData].get
        case 500 => AnswerData((response.json \ "id").as[Long], "[INTERNAL SERVER ERROR]")
      }
    }

    futureResult
  }
}
