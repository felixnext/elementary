package elementary.util.data

import com.typesafe.config.ConfigFactory
import scala.concurrent.Future
import scala.util.Try
import scalaj.http.{HttpException, Http, HttpResponse}
import spray.json._

case class SpotlightResult(uri: String, support: Int, types: Seq[String], surfaceForm: String, offset: Int, score: Double)

object DBpediaSpotlight extends DefaultJsonProtocol {
  import scala.concurrent.ExecutionContext.Implicits.global

  // some json conversion
  implicit object ResultJsonFormat extends RootJsonFormat[Option[SpotlightResult]] {
    def write(c: Option[SpotlightResult]) = JsObject( "@URI" -> JsString(c.get.uri) )
    def read(value: JsValue) = {
      value.asJsObject.getFields("@URI", "@support", "@types", "@surfaceForm", "@offset", "@similarityScore") match {
        case Seq(JsString(uri), JsString(support), JsString(types), JsString(form), JsString(offset), JsString(score)) =>
          Some( new SpotlightResult("<" + uri + ">", support.toInt, types.split(",").toSeq, form, offset.toInt, score.toDouble) )
        case _ => None
      }
    }
  }

  // load the config data from file
  lazy val (address, confidence, support) = {
    val config = ConfigFactory.load()
    (
      config.getString("elementary.util.data.spotlight.address"),
      config.getDouble("elementary.util.data.spotlight.confidence"),
      config.getInt("elementary.util.data.spotlight.support")
    )
  }

  // searches all dbpedia entities from the text
  def search(text: String): Future[List[SpotlightResult]] = {
    // defines the length threshold for the spotlight data
    val maxLength = 500

    // execute a single search on the list
    def execSearch(text: String, offset: Int): Future[List[SpotlightResult]] = {
      val response: Future[HttpResponse[String]] = Future {
        Http(address + "/rest/annotate")
        .param("confidence", confidence.toString)
        .param("support", support.toString).param("text", text)
        .header("Accept", "application/json").timeout(connTimeoutMs = 2000, readTimeoutMs = 700000).asString
      }
      response.map(msg => parseResponse(msg.body, offset))
    }

    // split the input data into tuples (string and offset)
    @annotation.tailrec
    def loop(words: List[String], text: String = "", offset: Int = 0, res: List[(String, Int)] = List()): List[(String, Int)] = {
      words match {
        case word :: tail if (word.length + text.length) > maxLength => loop(tail, word, offset + text.length + 1, (text, offset) :: res)
        case word :: tail => loop(tail, text + " " + word, offset, res)
        case Nil if text.length > 0 => (text, offset) :: res
        case Nil => res
      }
    }

    @annotation.tailrec
    def loopRes(data: List[(String, Int)], fut: Future[List[SpotlightResult]] = Future(List())) : Future[List[SpotlightResult]] = {
      data match {
        case pair :: tail => loopRes( tail, fut.flatMap(ls => execSearch(pair._1, pair._2).map(ls2 => ls ::: ls2)) )
        case Nil => fut
      }
    }

    val pairs = loop(text.split(" ").toList.map(_.trim))
    loopRes(pairs)
  }

  def parseResponse(response: String, offset: Int = 0): List[SpotlightResult] = {
    val ls = Try(response.parseJson).toOption.flatMap(json => json.asJsObject.getFields("Resources").headOption) match {
      case Some(res) =>
        res.convertTo[List[Option[SpotlightResult]]].filterNot(_ == None).map(_.get)
      case None => List()
    }
    ls.map(d => SpotlightResult(d.uri, d.support, d.types, d.surfaceForm, d.offset + offset, d.score))
  }
}
