package elementary.statistic

import akka.actor.{ActorSystem, Actor, ActorRef, ActorLogging, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri, HttpEntity, MediaTypes}
import akka.http.scaladsl.model.HttpMethods._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Sink, Source }
import com.typesafe.config.ConfigFactory
import elementary.util.cluster.ClusterMaster
import elementary.statistic.views.AllRoutes
//import kamon.Kamon
import scala.concurrent.duration._
import scala.concurrent.Future

//! Defines the app for the statistic framework
object StatisticApp extends App  {
  // start kamon monitoring
  val config = ConfigFactory.load()
  //Kamon.start()

  implicit val system = ActorSystem("StatisticSystem")
  implicit val materializer = ActorMaterializer()

  val log = Logging(system, getClass)

  // start the remote actor
  system.actorOf(Props[RemoteActor], name = "remote")
  system.actorOf(Props[AnalysisActor], name = "analysis")

  val bindingFuture = Http().bindAndHandle(
    AllRoutes.route,
    config.getString("elementary.statistic.http.interface"),
    config.getInt("elementary.statistic.http.port")
  )

  // shutdown the system (might make a timeout?)
  system.awaitTermination()
  system.registerOnTermination {
    // stop kamon monitoring
    //Kamon.shutdown()
    AllRoutes.close
  }
}
