package elementary.api

import akka.actor.{ActorSystem, Actor, ActorRef, ActorLogging, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri, HttpEntity, MediaTypes}
import akka.http.scaladsl.model.HttpMethods._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Sink, Source }
import com.typesafe.config.ConfigFactory
import elementary.api.views.ViewFlow
import elementary.util.cluster.ClusterMaster
//import kamon.Kamon
import scala.concurrent.Future

//! Implements the service and starts the server (main method)
object ApiServer extends App {
  // start kamon monitoring
  val config = ConfigFactory.load()
  //Kamon.start()

  // create the actor and stream system
  implicit val system = ActorSystem("ApiSystem")
  implicit val materializer = ActorMaterializer()

  // create the master actor as frontend
  val frontend = system.actorOf(ClusterMaster.props, name = "frontend")
  val actor = system.actorOf(Props(classOf[PromiseActor], frontend), name = "promise-actor")
  val log = Logging(system, getClass)

  // create the http source that is bound to the
  val serverSource: Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
    Http(system).bind(interface = config.getString("elementary.api.http.interface"), port = config.getInt("elementary.api.http.port"))

  // materialize the server binding
  val bindingFuture: Future[Http.ServerBinding] = serverSource.to(Sink.foreach { connection =>
    log.info("Accepted new connection from " + connection.remoteAddress)

    // push the connection info into the flow (send to statistic framework)
    connection handleWithAsyncHandler ViewFlow.requestHandler(actor, connection.remoteAddress)
    //connection handleWith { Flow[HttpRequest] map ViewFlow.requestHandler }
  }).run()

  // wait for system shutdown
  system.awaitTermination()
  system.registerOnTermination {
    // stop kamon monitoring
    //Kamon.shutdown()
  }
}
