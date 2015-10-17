package elementary.util.logging

import scala.io._
import java.io._
import elementary.util.logging.Messages._

//! Abstract base class for all available log levels
abstract class LogLevel

//! Log Level for error messages
case class LogError() extends LogLevel

//! Log Level for warnings
case class LogWarning() extends LogLevel

//! Log Level for debug messages
case class LogDebug() extends LogLevel

//! Log Level for info messages
case class LogInfo() extends LogLevel

//! Defines a log message
case class LogMessage(time : Long, priority : LogLevel, msg : String, sender : String) {
  //! Converts this message to string
  override def toString() : String = {
    getTimeString(time) + " \t[" + sender + ": \t" + priorityString + "] \t" + msg
  }

  //! Converts the given time long into
  def getTimeString(time : Long) : String = {
    val date = new java.util.Date(time)
    val df = new java.text.SimpleDateFormat(LogManager.timeformat)

    df.format(date)
  }

  //! Converts the priority into a string
  val priorityString = {
    priority match {
      case LogError() => "error"
      case LogWarning() => "warning"
      case LogDebug() => "debug"
      case LogInfo() => "info"
    }
  }
}

//! Defines the companion object for the log class
object LogMessage {
  val pattern = """([0-9]+\.[0-9]+\.[0-9]+ - [0-9]+:[0-9]+:[0-9]+.[0-9]+) \t?\[(.*?): \t?([A-Za-z]+)\] \t?(.*)""".r

  //! Parse a log message from string
  def fromString(input : String) : Option[LogMessage] = {
    try {
      val pattern(dateStr, sender, priority, msg) = input
      val sdf = new java.text.SimpleDateFormat(LogManager.timeformat);
      val date = sdf.parse(dateStr);

      val prior : LogLevel = priority match {
        case "error" => LogError()
        case "warning" => LogWarning()
        case "debug" => LogDebug()
        case _ => LogInfo()
      }
      Some(LogMessage(date.getTime, prior, msg, sender))
    }
    catch {
      case _: Throwable => None
    }
  }
}

//! Defines the manager for the log
object LogManager {
  //! Holds the name of the log file
  val logfile = "log.txt"

  //! Holds the time format
  val timeformat = "dd.MM.yyyy - HH:mm:ss.SS"

  //! Holds the writer to the log file (working in append mode)
  val writer = new PrintWriter(new FileWriter(logfile, true))

  /**
  * Inserts a log statement
  * @param lvl the level at which the statement is inserted
  * @param msg the message that should be entered
  **/
  def log(lvl : LogLevel, msg : String, sender : String) = {
    //process: build the message and print it
    val log_msg = LogMessage(getTime(), lvl, msg, sender)
    writer.println(log_msg.toString)
    writer.flush
  }

  //! logs an error message
  def error(msg : String, sender : String) = {
    log(LogError(), msg, sender)
  }

  //! logs a warning
  def warning(msg : String, sender : String) = {
    log(LogWarning(), msg, sender)
  }

  //! logs a debug message
  def debug(msg : String, sender : String) = {
    log(LogDebug(), msg, sender)
  }

  //! logs a info message
  def info(msg : String, sender : String) = {
    log(LogInfo(), msg, sender)
  }

  //! Returns the current time
  def getTime() : Long = {
    System.currentTimeMillis
  }

  //! Returns all log messages for the given time period
  def getLog(start: Long = 0, end: Long = Long.MaxValue): Option[List[LogMessage]] = {
    //safty: check if times match
    if (start > end) None

    // TODO rewrite as functional code
    /*var ls : List[LogMessage] = List()

    //process: get the log data
    try {
      for (line <- Source.fromFile(logfile).getLines()) {
        if (line.length > 24) {
          //process: get the time of the current line
          val sdf = new java.text.SimpleDateFormat(timeformat);
          val date = sdf.parse(line.substring(0, 24));
          val time = date.getTime

          //process: check if the time matches
          if (time >= start && time <= end) {
            LogMessage.fromString(line) match {
              case Some(msg) => ls = ls :+ msg
              case None => ()
            }
          }
          else if (time > end) {
            return Some(ls)
          }
        }
      }
      Some(ls)
    }
    catch {
      case e: Throwable => {
        e.printStackTrace()
        None
      }
    }*/
    None
  }

  //! Deletes the current log file
  def clean() : Boolean = {
    try {
      writer.flush
      val file = new java.io.File(logfile)
      file.delete
      //writer = new PrintWriter(new FileWriter(logfile))
      true
    }
    catch {
      case _: Throwable => false
    }
  }

  //! Closes the writer
  def close() : Boolean = {
    try {
      writer.close
      true
    }
    catch {
      case _: Throwable => false
    }
  }
}
