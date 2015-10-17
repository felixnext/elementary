import elementary.pipeline._
import akka.actor.{ActorSystem, Actor, Props, Identify, ActorIdentity}
import akka.testkit.{ TestActors, TestKit, ImplicitSender }
import org.scalatest.{WordSpecLike, Matchers, BeforeAndAfterAll}

class ClusterSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("BackendClusterSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val echo = system.actorOf(TestActors.echoActorProps)

  "Backend ClusterWorker Actor" must {
    "be creatable" in {
      val worker1 = system.actorOf(ClusterBackendActor.props(system.actorSelection(echo.path)), "worker1")
      Thread.sleep(100)
      worker1 ! Identify(1)
      expectMsg(ActorIdentity(1, Some(worker1)))

      system.stop(worker1)
    }
  }
}
