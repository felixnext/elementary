package elementary.util.logging

object Messages {

  //! Defines messages which answers are implemented into glue
  abstract class ControlMessage

  //! Requests to shutdown the system in the given amount of time
  case class Shutdown(time : Long = 0) extends ControlMessage

  //! Requests the uptime from the system
  case class GetUptime() extends ControlMessage

  //! Returns the uptime of the system
  case class Uptime(time : Long) extends ControlMessage

  //! Requests the log of a system for the specified time
  case class GetLog(start : Long, end : Long) extends ControlMessage

  //! Returns an error message
  case class ErrorMessage(code: Int, error: String) extends ControlMessage

  //! Returns the log messages for a certain amount of time
  case class LogPeriod(start : Long, end : Long, log : List[LogMessage]) extends ControlMessage

  //! Defines the Key Value Message that is passed by the user
  case class UserMessage(name: String, data: Map[String,String])
}
