package elementary.api.views


import akka.actor.ActorRef
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri, HttpEntity, MediaTypes}
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.{ToResponseMarshallable, Marshal}
import akka.http.scaladsl.unmarshalling.Unmarshal
import spray.json.DefaultJsonProtocol
import spray.json._
import akka.stream.Materializer
import akka.pattern.ask
import akka.util.Timeout
import elementary.util.common.Communication._
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Success, Failure}
import java.net.InetSocketAddress
import com.typesafe.config.{ConfigFactory, Config}

// Some case classes for special requests
case class HttpQuestion(question: String, details: String)
case class QuestionBatch(name: String, questions: List[HttpQuestion])
case class HttpAnswer(id: Long, answer: String)
case class AnswerBatch(name: String, answers: List[HttpAnswer])

// Defines implicit conversions for json entities
trait Protocols extends DefaultJsonProtocol {
  implicit val questionFormat = jsonFormat2(HttpQuestion.apply)
  implicit val questionBatchFormat = jsonFormat2(QuestionBatch.apply)
  implicit val responseFormat = jsonFormat2(HttpAnswer)
  implicit val reponseBatchFormat = jsonFormat2(AnswerBatch)
}

object ViewFlow extends Protocols {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.language.postfixOps

  val config = ConfigFactory.load()

  // timeout for the ask pattern
  implicit val timeout = Timeout(config.getInt("elementary.api.timeout") seconds)

  // create the request handler (note: frontend should be the promise actor!)
  def requestHandler(frontend: ActorRef, address: InetSocketAddress)(implicit mat: Materializer): HttpRequest => Future[HttpResponse] = {
    // Standard ask route
    case HttpRequest(POST, Uri.Path("/ask"), _, ent, _) if (ent.contentType().mediaType == MediaTypes.`application/json`) =>
      val reqFuture = Unmarshal(ent).to[HttpQuestion]
      reqFuture flatMap { req =>
        val future: Future[AnswerMessage] = ask(frontend, QuestionRequest(req.question, address.toString(), req.details)).mapTo[AnswerMessage]
        future.map(_ match {
          case Answer(id, text)   => HttpResponse(200, entity = HttpAnswer(id, text).toJson.toString)
          case AnswerUnkown(id)   => HttpResponse(200, entity = s"""{"id":$id, "type":"error", "msg":"answer unkown"}""")
          case NoAnswer(id)       => HttpResponse(200, entity = s"""{"id":$id, "type":"error", "msg":"no answer found"}""")
          case QuestionDenied(id) => HttpResponse(500, entity = s"""{"id":$id}""")
        })
      }
    // batch route
    case HttpRequest(POST, Uri.Path("/ask/batch"), _, ent, _) if (ent.contentType().mediaType == MediaTypes.`application/json`) =>
      val reqFuture = Unmarshal(ent).to[QuestionBatch]
      reqFuture flatMap { ls =>
        val resLs: Future[List[AnswerMessage]] = Future sequence ls.questions.map( req =>
          ask(frontend, QuestionRequest(req.question, address.toString(), req.details)).mapTo[AnswerMessage]
        )
        // TODO convert
        Future(HttpResponse(200))
      }
    // info page
    case HttpRequest(GET, Uri.Path("/info"), _, _, _) =>
      Future {
        HttpResponse(
          entity = HttpEntity(MediaTypes.`application/json`, api.build.BuildInfo.toJson))
      }

    // index page
    case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      Future { HttpResponse(entity = "PONG!") }

    case _: HttpRequest =>
      Future { HttpResponse(404, entity = "Unknown resource!") }
  }
}
