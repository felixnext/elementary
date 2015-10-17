import collection.mutable.Stack
import org.scalatest._
import elementary.util.logging.{LogManager, LogMessage}

class LogManagerSpec extends FlatSpec with Matchers {
  "The Log Manager" should "be able to clean the log" in {
    LogManager.clean()
  }

  it should "be able to log information" in {
    LogManager.warning("Log Data 1", "Test")
    LogManager.error("Log Data 2", "Test")
    LogManager.debug("Log Data 3", "Test")
  }

  it should "return the logged data" in {
    val ls_opt = LogManager.getLog()
    ls_opt should not be (None)
    val ls: List[LogMessage] = ls_opt.getOrElse(List())
    ls.head.msg should be ("Log Data 1")
    ls.head.sender should be ("Test")
    ls.last.msg should be ("Log Data 3")
  }
}
