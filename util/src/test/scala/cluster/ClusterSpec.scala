import akka.actor.ActorSystem
import akka.actor.{Actor, Props, ActorSelection, ActorRef, ActorSystem, Identify, ActorIdentity}
import akka.testkit.{ TestActors, TestKit, ImplicitSender }
import akka.util.Timeout
import akka.pattern.ask
import org.scalatest.{WordSpecLike, Matchers, BeforeAndAfterAll}
import scala.concurrent.duration._
import scala.concurrent.{Future, Await}

import elementary.util.cluster._

// Sample Worker Actor using the Cluster Protocol
class WorkerActor(master: ActorSelection, green: Long, red: Long, ret: Boolean) extends ClusterWorker(master, green, red) {
  import scala.concurrent.ExecutionContext.Implicits.global

  def doWork(workSender: ActorRef, work: Any): Unit = {
    Future {
      Thread.sleep(1000)
      if(ret)
        workSender ! work
      self ! ClusterProtocol.WorkComplete(work)
    }
  }
}

object WorkerActor {
  def props(master: ActorSelection, ret: Boolean = false): Props = {
    Props(classOf[WorkerActor], master, 2.toLong, 10.toLong, ret)
  }
}

class ClusterSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("ClusterSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val echo = system.actorOf(TestActors.echoActorProps)

  "ClusterWorker Actor" must {
    "be creatable" in {
      val worker1 = system.actorOf(WorkerActor.props(system.actorSelection(echo.path)), "worker1")
      Thread.sleep(100)
      worker1 ! Identify(1)
      expectMsg(ActorIdentity(1, Some(worker1)))

      system.stop(worker1)
    }
    "return status according to load" in {
      import ClusterProtocol._
      val worker2 = system.actorOf(WorkerActor.props(system.actorSelection(echo.path)), "worker2")
      Thread.sleep(100)
      worker2 ! WorkerStatus(1)
      expectMsg(GreenState(1))
      (1 to 5).foreach( _ => worker2 ! WorkToBeDone(2) )
      Thread.sleep(100)
      worker2 ! WorkerStatus(2)
      expectMsg(OrangeState(2))
      (1 to 10).foreach( _ => worker2 ! WorkToBeDone(3) )
      Thread.sleep(2000)
      worker2 ! WorkerStatus(3)
      expectMsg(RedState(3))

      system.stop(worker2)
    }
  }

  "ClusterMaster Actor" must {
    "be creatable" in {
      val master1 = system.actorOf(ClusterMaster.props, "master1")
      Thread.sleep(100)
      master1 ! Identify(1)
      expectMsg(ActorIdentity(1, Some(master1)))

      system.stop(master1)
    }
  }

  "ClusterNode Actor" must {
    "be creatable" in {
      val func: (ActorSelection => Props) = master => WorkerActor.props(master, false)
      val node1 = system.actorOf(NodeMaster.props(func), "node1")
      Thread.sleep(100)
      node1 ! Identify(1)
      expectMsg(ActorIdentity(1, Some(node1)))

      system.stop(node1)
    }
  }

  "Cluster System" must {
    "be creatable and return a valid answer" in {
      import ClusterProtocol._
      import scala.language.postfixOps

      val master2 = system.actorOf(ClusterMaster.props, "master2")
      val func: (ActorSelection => Props) = master => WorkerActor.props(master, true)
      val node2 = system.actorOf(NodeMaster.props(func, master2.path.toString), "node2")
      val node3 = system.actorOf(NodeMaster.props(func, master2.path.toString), "node3")
      Thread.sleep(100)
      // TODO test the setup
      //expectMsg(WorkIsReady(false))
      //expectMsgPF WorkerRequestsWork()
      /*ignoreMsg {
        case WorkIsReady(urgent) => msg != "something"
      }*/
      within(10 seconds) {
        master2 ! "yolo!"
        expectMsg("yolo!")
      }

      system.stop(master2)
      system.stop(node2)
      system.stop(node3)
    }

    "be askable" in {
      import scala.language.postfixOps

      val master3 = system.actorOf(ClusterMaster.props, "master3")
      val func: (ActorSelection => Props) = master => WorkerActor.props(master, true)
      val node4 = system.actorOf(NodeMaster.props(func, master3.path.toString), "node4")
      val node5 = system.actorOf(NodeMaster.props(func, master3.path.toString), "node5")
      Thread.sleep(100)

      implicit val timeout = Timeout(5 seconds)
      val spaceTime = (master3 ? "swag")
      val res = Await.result(spaceTime, timeout.duration).asInstanceOf[String]

      res should be ("swag")

      system.stop(master3)
      system.stop(node4)
      system.stop(node5)
    }
  }
}
