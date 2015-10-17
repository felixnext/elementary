package elementary.util.cluster

import akka.actor.ActorRef

object ClusterProtocol {
  // Messages from Workers
  case class WorkerCreated(worker: ActorRef)
  case class WorkerRequestsWork(worker: ActorRef)
  case class WorkIsDone(worker: ActorRef, remaining: Int)   // TODO: Add ID here
  case class WorkIsDenied()

  // Messages to Workers
  case class WorkToBeDone(work: Any)                        // TODO: Add ID here
  case class WorkIsReady(urgent: Boolean)
  case object NoWorkToBeDone
  case object WorkerShutdown
  case class WorkComplete(result: Any)
  case class WorkerStatus(time: Long)

  // Messages to NodeMaster
  case object RequestWorkerStart
  case object RequestWorkerStop
  case object RequestNodeStatus

  // Messages from NodeMaster
  case class NodeStatus(workers: Int)

  // Status codes
  abstract trait ClusterState
  case class GreenState(time: Long) extends ClusterState
  case class OrangeState(time: Long) extends ClusterState
  case class RedState(time: Long) extends ClusterState
}
