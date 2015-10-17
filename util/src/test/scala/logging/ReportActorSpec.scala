import elementary.util.logging.ReportActor
import elementary.util.common.Communication._
import akka.actor.{ActorSystem, Actor, Props, ActorLogging}
import akka.testkit.{ TestActors, TestKit, ImplicitSender, DefaultTimeout }
import com.typesafe.config.{ConfigFactory, Config}
import scala.concurrent.duration._
import scala.language.postfixOps
import org.scalatest.{WordSpecLike, Matchers, BeforeAndAfterAll}

class ReportActorSpec(_system: ActorSystem) extends TestKit(_system)
  with WordSpecLike with Matchers with BeforeAndAfterAll
  with DefaultTimeout with ImplicitSender {

  //import TestKitUsageSpec._

  def this() = this(ActorSystem("ReportActorSpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val echo = system.actorOf(TestActors.echoActorProps, "echo")

  "The Pipeline Report Actor" must {
    "have a valid props companion" in {
      val config = ConfigFactory.load()
      config.getString("elementary.statistics.address") should === ("akka.tcp://StatisticSystem@10.0.0.1:2552/user/remote")
      config.getInt("elementary.util.logging.max-repeat") should === (3)
      ReportActor.props should === (Props(classOf[ReportActor], "akka.tcp://StatisticSystem@10.0.0.1:2552/user/remote", 3))
    }

    "repeat messages to the remote actor" in {
      val report = system.actorOf(Props(classOf[ReportActor], self.path.toString, 2), name = "reporter")
      Thread.sleep(100)
      val time = System.currentTimeMillis
      report ! BasicReportMessage(1, "hello", time)
      expectMsg(BasicReportMessage(1, "hello", time))

      system.stop(report)
    }
  }
}
