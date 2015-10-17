package elementary.statistic.views


import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, HttpEntity, MediaTypes}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.scaladsl.{ Flow, Sink, Source }
import spray.json._
import akka.stream.Materializer
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Success, Failure}

import elementary.util.common.Communication._
import elementary.statistic.models._

// Defines implicit conversions for json entities
trait CorporaProtocols extends DefaultJsonProtocol {
  implicit val corporaHullFormat = jsonFormat3(CorporaHull.apply)
  implicit val corpusDataFormat = jsonFormat2(CorpusInfo.apply)
  implicit val questionHullFormat = jsonFormat4(QuestionHull.apply)
  implicit val questionDataFormat = jsonFormat6(QuestionInfo.apply)
  implicit val corporaDataFormat = jsonFormat4(CorporaInfo.apply)
}

object CorporaRoutes extends CorporaProtocols {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.language.postfixOps

  // create connection to corpus db
  val corpora = Corpora.create
  // timeout for the ask pattern
  implicit val timeout = Timeout(5 seconds)

  def route(implicit mat: Materializer): Route =
    pathPrefix("corpora") {
      pathEnd {
        get {
          complete {
            val ls = corpora.list
            ls.map(list => HttpResponse(OK, entity =  HttpEntity(MediaTypes.`application/json`, list.toJson.toString) ))
          }
        }
      } ~
      path("create") {
        post {
          entity(as[CorpusInfo]) { corpus =>
            complete {
              // check if the corpus exists
              val check = corpora.exists(corpus.name)
              check.flatMap(exists => {
                if (exists) Future(HttpResponse(Conflict, entity = HttpEntity(MediaTypes.`application/json`,
                  s"""{"success": false, "operation": "create", "name": "${corpus.name}", "error": "duplicate"}""") ))
                else {
                  val res = corpora.create(corpus)
                  res.map(_ => HttpResponse(OK, entity =  HttpEntity(MediaTypes.`application/json`,
                    s"""{"success": true, "operation": "create", "name": "${corpus.name}"}""") ))
                }
              })
            }
          }
        }
      } ~
      pathPrefix(Segment) { corpusName =>
        pathEnd {
          get {
            complete {
              val corpus = corpora.get(corpusName)
              corpus.map(c => HttpResponse(OK, entity = HttpEntity(MediaTypes.`application/json`, c.toJson.toString) ))
            }
          }
        } ~
        path("deprecate") {
          get {
            complete {
              val res = corpora.deprecate(corpusName)
              res.map(_ => HttpResponse(OK, entity = HttpEntity(MediaTypes.`application/json`,
                s"""{"success": true, "operation": "deprecate", "name": "$corpusName"}""") ))
            }
          }
        } ~
        path("question" / "create") {
          post {
            entity(as[QuestionHull]) { question =>
              complete {
                val res = corpora.addQuestion(corpusName, question)
                res.map(id => HttpResponse(OK, entity = HttpEntity(MediaTypes.`application/json`,
                  s"""{"success": true, "operation": "question/create", "name": "$corpusName", "id": $id}""") ))
              }
            }
          }
        } ~
        path("question" / IntNumber / "deprecate") { qid =>
          get {
            complete {
              val res = corpora.deprecateQuestion(corpusName, qid)
              res.map(_ => HttpResponse(OK, entity = HttpEntity(MediaTypes.`application/json`,
                s"""{"success": true, "operation": "question/deprecate", "name": "$corpusName", "id": $qid}""") ))
            }
          }
        }
      }
    }
}
