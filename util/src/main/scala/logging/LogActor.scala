package elementary.util.logging

import akka.actor.Actor
import akka.actor.ActorRef
import elementary.util.logging.Messages._

//! Defines the messaging actor that should be implemented by all user code
abstract class LogActor extends Actor {

  //! Holds the time at which this actor is created
  val start_time = System.currentTimeMillis
  LogManager.info("Message Actor Started", "MessageActor")

  //! Receives incoming messages
  def receive = {
    case Shutdown(time) => {
      //process: close the log
      LogManager.info("shuting down by command", "MessageActor")
      LogManager.close
      //process: shutdown the actor system
      context.system.shutdown()
    }

    case GetUptime => {
      sender ! Uptime(System.currentTimeMillis - start_time)
    }

    case GetLog(start, end) => {
      //process: get the list of all logs
      LogManager.getLog(start, end) match {
        case Some(logs) => {
          //process: split the logs in order to reduce message size
          def sendLogs(logs: List[LogMessage]): Unit = {
            if (logs.size > 20) {
              val log_send = logs.take(20)
              sender ! LogPeriod(log_send.head.time, log_send.last.time, log_send)
              sendLogs(logs.drop(20))
            }
            else
              sender ! LogPeriod(start, end, logs)
          }

          sendLogs(logs)
        }
        case None => sender ! ErrorMessage(101, "Unable to retrieve logs")
      }
    }

    case UserMessage(name, data) => execute(data)(name)

    case x => receiveExt(x)
  }

  //! Defines the messages that are parsed by the user
  def execute(data : Map[String,String]): LogActor.Execute

  //! Defines the extended receive function
  def receiveExt: Actor.Receive
}

//! Companion object for the Message Actor
object LogActor {
  type Execute = PartialFunction[String, Unit]
}
