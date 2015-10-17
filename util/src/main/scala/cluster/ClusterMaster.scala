package elementary.util.cluster

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, Props, Terminated}
import com.typesafe.config.ConfigFactory
import scala.collection.mutable.{Map, Queue}
import scala.concurrent.Future

//! Main master that should be started on the
class ClusterMaster(critical: Int) extends Actor with ActorLogging {
  import ClusterProtocol._

  // Holds known workers and what they may be working on
  val workers = Map.empty[ActorRef, List[Tuple2[ActorRef, Any]]]
  // Holds the master mailbox with pending work
  val workQ = Queue.empty[Tuple2[ActorRef, Any]]

  // Notification to all workers that new work is ready
  def notifyWorkers(): Unit = {
    // check if there is work
    if (!workQ.isEmpty) {
      // check if there are workers
      if(workers.isEmpty) log.error("worker list is empty!")
      else log.info("notify {} workers", workers.size)

      // notify them
      val urgent = workQ.length > critical
      workers.foreach {
        //case (worker, m) if (m.isEmpty) =>
        case (worker, m) => worker ! WorkIsReady(urgent)
        case e => log.error("unkown entry in worker list: [{}]", e)
      }
    } else log.info("no work to notify about!")
  }

  def receive = {
    // Add the new worker to the worker-list
    case WorkerCreated(worker) =>
      log.info("Worker created: {}", worker)
      context.watch(worker)
      workers += (worker -> Nil)
      notifyWorkers()

    // Send work to the worker that requested (if there is any)
    case WorkerRequestsWork(worker) =>
      log.info("Worker requests work: {}", worker)
      if (workers.contains(worker)) {
        if (workQ.isEmpty)
          worker ! NoWorkToBeDone
        else {
          val (workSender, work) = workQ.dequeue()
          val ls = workers(worker)
          workers += (worker -> (ls :+ (workSender -> work)))
          worker.tell(WorkToBeDone(work), workSender) // tell the worker as if the message would come from workSender
          // notify workers if the load is critical
          if (workQ.length > critical)
            notifyWorkers()
        }
      }

    // Worker has completed its work and we can clear it out
    case WorkIsDone(worker, count) =>
      if (!workers.contains(worker))
        log.error("Unknown worker {} completed work...", worker)
      else {
        // remove the first element for this worker
        val ls = workers(worker)
        val i = ls.indexWhere(x => x._1 == worker)
        workers += ( worker -> ( ls.take(i) ::: ls.takeRight(ls.size - i - 1) ) )
        // notify the free worker that there is critical work available
        if (workQ.length > critical)
          worker ! WorkIsReady(true)
      }

    // Worker or Node dies message / check if the worker was currently processing (reschedule)
    case Terminated(worker) =>
      if(workers.contains(worker) && (workers(worker) != Nil)) {
        log.error("Worker {} died while processing {}", worker, workers(worker))
        val ls = workers(worker)
        ls.foreach(tpl => self.tell(tpl._2, tpl._1))
      }
      workers -= worker

    // All others messages are seen as work
    case work =>
      log.info("Queueing {}", work)
      workQ.enqueue(sender -> work)
      notifyWorkers()
  }
}

object ClusterMaster {
  lazy val props: Props = {
    val config = ConfigFactory.load()
    Props(classOf[ClusterMaster], config.getInt("elementary.util.cluster.master-critical"))
  }
}
