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

object AllRoutes {
  def route(implicit mat: Materializer): Route =
    CorporaRoutes.route ~
    StatisticRoutes.route ~
    path("info") {
      get {
        complete {
          HttpResponse(
            entity = HttpEntity(MediaTypes.`application/json`, statistics.build.BuildInfo.toJson))
        }
      }
    } ~
    path(Rest) { rest =>
      (get | post) {
        complete {
          HttpResponse(404, entity = "Path is not available")
        }
      }
    }

  def close() = {
    CorporaRoutes.corpora.close
  }
}

/*def auth = HttpBasicAuthenticator.provideUserName {
  case p @ UserCredentials.Provided(name) ⇒ p.verifySecret(name + "-password")
  case _                                  ⇒ false
}


path("secure") {
  HttpBasicAuthentication("My very secure site")(auth) { user ⇒
    complete(HttpResponse(
      entity = HttpEntity(MediaTypes.`text/html`, "<html><body>Hello <b>{ user }</b>. Access has been granted!</body></html>")))
    }
  }*/
