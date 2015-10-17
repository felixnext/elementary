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
import elementary.util.data._

// Defines implicit conversions for json entities
trait StatisticProtocols extends DefaultJsonProtocol {
  implicit val processDetailsFormat = jsonFormat4(ProcessDataDetails.apply)
  implicit val processFormat = jsonFormat11(ProcessData.apply)
}

object StatisticRoutes extends StatisticProtocols {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.language.postfixOps

  // create connection to corpus db
  //val corpora = Corpora.create
  // timeout for the ask pattern
  implicit val timeout = Timeout(5 seconds)

  def route(implicit mat: Materializer): Route =
    pathPrefix("stats") {
      pathPrefix("bson") {
        path("latest") {
          get {
            complete {
              ProcessDataMerger.latest.map(opt => opt match {
                case Some(data) => HttpResponse(OK, entity = HttpEntity(MediaTypes.`application/json`, data.toJson.prettyPrint))
                case None => HttpResponse(NotFound, entity = HttpEntity(MediaTypes.`application/json`, """{"error": "no data found"}"""))
              })
            }
          }
        } ~
        path("last" / IntNumber) { num =>
          get {
            complete {
              ProcessDataMerger.newest(num).map(opt => opt match {
                case Nil => HttpResponse(NotFound, entity = HttpEntity(MediaTypes.`application/json`, """{"error": "no data found"}"""))
                case data => HttpResponse(OK, entity = HttpEntity(MediaTypes.`application/json`, data.toJson.prettyPrint))
              })
            }
          }
        } ~
        path(LongNumber) { pid =>
          get {
            complete {
              ProcessDataMerger.get(pid).map(opt => opt match {
                case Some(data) => HttpResponse(OK, entity = HttpEntity(MediaTypes.`application/json`, data.toJson.prettyPrint))
                case None => HttpResponse(NotFound, entity = HttpEntity(MediaTypes.`application/json`, """{"error": "no data found"}"""))
              })
            }
          }
        }
      }
    }
}
