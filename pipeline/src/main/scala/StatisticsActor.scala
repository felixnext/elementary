package elementary.pipeline

import elementary.util.logging.ReportActor
import akka.actor.{ActorSystem, ActorRef, Props}

object StatisticsActor {
  def get: ActorRef = PipelineApp.system.actorOf(ReportActor.props)
}
