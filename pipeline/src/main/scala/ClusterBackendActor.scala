package elementary.pipeline


import akka.actor.{Actor, ActorRef, ActorLogging, Props, ActorPath, ActorSelection}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, Sink}
import com.typesafe.config.ConfigFactory
import scala.concurrent.Future

import elementary.util.logging.ReportActor
import elementary.util.cluster.ClusterWorker
import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._


//! Actor that receives work from the cluster and pushes it to the reactive stream
class ClusterBackendActor(master: ActorSelection, greenZone: Long, redZone: Long)
  extends ClusterWorker(master, greenZone, redZone) {

  // Materializer for the reactive stream
  implicit val materializer = ActorMaterializer()

  // create the stream with an actor source
  val source = Source.actorPublisher[QueryRef](SourceActor.props(master))
  val sinkActor = context.actorOf(SinkActor.props(self))
  val sink = Sink.actorRef(sinkActor, PipelineShutdown)
  val sourceActor = RootPipeline.flow.to(sink).runWith(source)
  val reporter = context.actorOf(ReportActor.props, "reporter")

  // push the work on the stream
  def doWork(workSender: ActorRef, work: Any) = {
    work match {
      case q: Question =>
        log.info("Worker Actor received work: {}", work)
        reporter ! PipelineReportMessage(q.id, self.path.toSerializationFormat, System.currentTimeMillis)
        sourceActor ! QueryRef(q.id, q.query, workSender)
      case x => log.error("Got wrong work request: {}", x)
    }
  }
}

object ClusterBackendActor {
  def props(master: ActorSelection): Props = {
    val config = ConfigFactory.load()
    Props(classOf[ClusterBackendActor],
      master,
      config.getLong("elementary.util.cluster.zones.green"),
      config.getLong("elementary.util.cluster.zones.red")
    )
  }
}
