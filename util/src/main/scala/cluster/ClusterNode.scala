package elementary.util.cluster

import akka.actor.{Actor, ActorLogging, ActorPath, ActorRef, ActorSelection, Props, ActorIdentity, Identify}
import akka.agent.Agent
import com.typesafe.config.ConfigFactory
import scala.collection.mutable.{Map, Queue}
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

//! Master of a single node that should be started once on each system
class NodeMaster(masterPath: String, propsFunc: ActorSelection => Props, config: NodeMaster.NodeConfig)
  extends Actor with ActorLogging {
  import scala.concurrent.ExecutionContext.Implicits.global
  import ClusterProtocol._

  case class CheckLoad(counter: Int)

  // Defines internal counter to check for nodes
  val lowCount = Agent(0)
  val highCount = Agent(0)
  val timeCount: Agent[Long] = Agent(0)

  // create the props for the worker
  //log.error("master path is: {}", masterPath)
  val masterSel = context.actorSelection(masterPath)
  val workerProps = propsFunc(context.actorSelection(masterPath))

  // before start, wait for the master to become available
  override def preStart(): Unit = {
    import scala.util.{Success, Failure, Try}
    import akka.pattern.ask
    import scala.language.postfixOps
    import akka.util.Timeout

    implicit val to = Timeout(10 seconds)

    @annotation.tailrec
    def loop(): Unit = {
      val fut = ask(masterSel, new Identify(1)).mapTo[ActorIdentity]
      Await.ready(fut, to.duration).value match {
        case opt: Option[Try[ActorIdentity]] if !opt.isEmpty && opt.get.isSuccess && !opt.get.toOption.get.ref.isEmpty =>
          log.info("reached master")
        case _ =>
          log.error("Could not reach master. Try again in 1000 secs.")
          Thread.sleep(1000)
          loop()
      }
    }
    loop()
    // start the basic number of workers
    (1 to config.minWorker).foreach(_ => context.actorOf(workerProps))
  }

  self ! CheckLoad(0)

  def receive = {
    // check the load
    case CheckLoad(counter) if config.adaptive => {
      lowCount send 0
      highCount send 0
      val time: Long = System.currentTimeMillis
      timeCount send time
      context.children foreach (_ ! WorkerStatus(time))

      Future {
        Thread.sleep(config.timeout)
        val workers = (context.children.size * config.loadThreshold).toInt
        val newCount =
          if (lowCount() >= workers) counter - 1
          else if (highCount() >= workers) counter + 1
          else if (counter > 0) counter - 1
          else if (counter < 0) counter + 1
          else counter
        val checkCount =
          if (newCount >= config.loadCount) {
            self ! RequestWorkerStart
            0
          }
          else if (newCount <= -config.loadCount) {
            self ! RequestWorkerStop
            0
          }
          else newCount
        self ! CheckLoad(checkCount)
      }
    }

    // Load messages from worker
    case GreenState(time) if (time == timeCount()) =>
      lowCount.send(_ + 1)
    case RedState(time) if (time == timeCount()) =>
      highCount.send(_ + 1)

    // master messages
    case RequestWorkerStart => {
      if (context.children.size < config.maxWorker)
        context.actorOf(workerProps)
      sender() ! NodeStatus(context.children.size)
    }
    case RequestWorkerStop => {
      val count = context.children.size
      if (count > config.minWorker)
        context.stop(context.children.head)
      sender() ! NodeStatus(count - 1)
    }
  }
}

object NodeMaster {
  // Holds the config for the Node Master
  case class NodeConfig(adaptive: Boolean, timeout: Long, loadThreshold: Double, loadCount: Int, maxWorker: Int, minWorker: Int)

  // Load the props from config file
  def props(propsFunc: ActorSelection => Props): Props = {
    val config = ConfigFactory.load()
    Props(classOf[NodeMaster],
      config.getString("elementary.util.cluster.master-address"),
      propsFunc,
      NodeConfig(
        config.getBoolean("elementary.util.cluster.adaptive"),
        config.getLong("elementary.util.cluster.timeout"),
        config.getDouble("elementary.util.cluster.load-threshold"),
        config.getInt("elementary.util.cluster.threshold-counter"),
        config.getInt("elementary.util.cluster.max-worker-per-node"),
        config.getInt("elementary.util.cluster.min-worker-per-node")
      )
    )
  }

  def props(propsFunc: ActorSelection => Props, masterPath: String): Props = {
    val config = ConfigFactory.load()
    Props(classOf[NodeMaster],
      masterPath,
      propsFunc,
      NodeConfig(
        config.getBoolean("elementary.util.cluster.adaptive"),
        config.getLong("elementary.util.cluster.timeout"),
        config.getDouble("elementary.util.cluster.load-threshold"),
        config.getInt("elementary.util.cluster.threshold-counter"),
        config.getInt("elementary.util.cluster.max-worker-per-node"),
        config.getInt("elementary.util.cluster.min-worker-per-node")
      )
    )
  }
}
