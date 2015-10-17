package elementary.util.cluster

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, ActorSelection, ActorSystem}
import akka.agent.Agent
import com.typesafe.config.ConfigFactory
import scala.concurrent.Future
import scala.util.{Success, Failure}

//! Base class for a worker actor in the cluster
abstract class ClusterWorker(master: ActorSelection, greenZone: Long, redZone: Long)
  extends Actor with ActorLogging {
  import scala.concurrent.ExecutionContext.Implicits.global
  import ClusterProtocol._

  // Holds the agent for the active work counter
  val counter = Agent(0)

  // Required to be implemented (works in a seperated future thread - actor stays responsive)
  def doWork(workSender: ActorRef, work: Any): Unit

  // Notify the master that this node is alive
  override def preStart() = master ! WorkerCreated(self)

  // receive function (start in idle state)
  def receive = {
    // Master says there's work to be done, let's ask for it
    case WorkIsReady(urgent) => {
      if (urgent || counter() <= redZone) {
        log.info("Requesting work")
        master ! WorkerRequestsWork(self)
      }
    }
    // Send the work off to the implementation (update counter)
    case WorkToBeDone(work) => {
      log.info("Got work {}", work)
      doWork(sender, work)
      counter send (_ + 1)
      if (counter() <= greenZone)
        master ! WorkerRequestsWork(self)
    }
    // Our derivation has completed its task
    case WorkComplete(result) => {
      log.info("Work completed with result {}.", result)
      counter send (_ - 1)
      master ! WorkIsDone(self, counter())
      if (counter() <= greenZone)
        master ! WorkerRequestsWork(self)
    }
    // Ignore (nothing to do anyway / might shut down?)
    case NoWorkToBeDone => {}

    // TODO worker unkown message here?

    // Message to request the worker status
    case WorkerStatus(time) => {
      sender() ! checkStatus(time)
    }
  }

  // checks the current status of the worker
  def checkStatus(time: Long): ClusterState = {
    val c = counter()
    if (c <= greenZone) GreenState(time)
    else if (c <= redZone) OrangeState(time)
    else RedState(time)
  }
}
