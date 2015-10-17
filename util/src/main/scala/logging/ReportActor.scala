package elementary.util.logging

import akka.actor.{ActorLogging, Actor, Props, Identify, ActorIdentity}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.{ConfigFactory, Config}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Try, Success, Failure}

import elementary.util.common.Communication._

//! Actor that sends information to the statistic framework
class ReportActor(remotePath: String, maxRepeat: Int = 3) extends Actor with ActorLogging {

  // defines the timeout for actor messages
  implicit val timeout = Timeout(15 seconds)

  // select the reference to the remote actor
  val statsActor = context.actorSelection(remotePath)

  // Handle the data for the
  def receive = {
    case msg: ReportMessage => sendReport(0, msg)
    case _ => sender() ! "failed"
  }

  // Tries to send the message three times, otherwise log error
  @annotation.tailrec
  final def sendReport(repeat: Int, msg: ReportMessage): Unit = {
    // ask the actor and wait for result
    if (repeat < maxRepeat) {
      // note: timeout is depended on try (wait longer for later tries)
      Try( Await.result(statsActor ? msg, timeout.duration * (repeat + 1)) ) match {
        case Success( ReportReceived(id) ) if id == msg.id => log.info("ReportReceived for question: {}", id)
        case Success( ReportReceived(id) ) =>
          log.error("Got wrong ReportReceived for question: got {} wanted {} / retry ...", id, msg.id)
          sendReport(repeat + 1, msg)
        case Success( m ) =>
          log.error("Received unkown message [{}] for report [{}] / retry ...", m, msg)
          sendReport(repeat + 1, msg)
        case Failure( e ) =>
          log.error("sending report [{}] failed with [{}] / retry ...", msg, e)
          sendReport(repeat + 1, msg)
      }
    }
    else {
      log.error("Could not reach Statistic Actor at [{}] / Message [{}] discarded / [{}] vs [{}]",
        remotePath, msg, repeat, maxRepeat)
    }
  }
}

object ReportActor {
  // define the props for the report actor, loading the remote path from config file
  lazy val props: Props = {
    val config = ConfigFactory.load()
    Props(classOf[ReportActor],
      config.getString("elementary.statistics.address"),
      config.getInt("elementary.util.logging.max-repeat")
    )
  }
}
