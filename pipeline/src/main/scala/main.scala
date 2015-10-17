package elementary.pipeline

import akka.actor.{ActorSystem, ActorPath, Props}
import akka.event.Logging
import akka.pattern.ask
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
//import kamon.Kamon
import scala.concurrent.duration._
import elementary.util.cluster.NodeMaster

//! Defines the app for the pipeline system
object PipelineApp extends App  {
  // start kamon monitoring
  //Kamon.start()

  // start the actor system and the materializer
  implicit val system = ActorSystem("PipelineSystem")
  val log = Logging(system, getClass)

  // DEBUG

  // start the remote actor that handles the stream
  system.actorOf(NodeMaster.props(ClusterBackendActor.props), name = "master")

  // shutdown the system (might make a timeout?)
  val duration = Duration(24, HOURS)
  system.awaitTermination()
  //system.awaitTermination(duration)
  system.registerOnTermination {
    // stop kamon monitoring (TODO check for shutdown?)
    //Kamon.shutdown()
  }

  //TODO restart
}
