import akka.actor.ActorSystem
import akka.actor.{Actor, Props}
import akka.testkit.{ TestActors, TestKit, ImplicitSender }
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll
import scala.concurrent.duration._

import elementary.util.logging.Messages._

//! Some Test actor that implements the abstract message actor class
class LogTestActor extends elementary.util.logging.LogActor {
  def execute(data : Map[String,String]) = {
    case _ => ()
  }

  def receiveExt = {
    case _ => ()
  }
}

class LogActorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("LogActorSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "An Message actor" must {
    "return it's current runtime" in {
      val actor = system.actorOf(Props[LogTestActor], "msgActor")
      actor ! GetUptime
      expectMsgPF() {
        case Uptime(time) if time >= 0 => true
      }
    }

  }
}
