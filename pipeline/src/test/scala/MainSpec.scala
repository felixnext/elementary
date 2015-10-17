import org.scalatest._
import org.scalatest.matchers.ShouldMatchers

class MainSpec extends FlatSpec with Matchers {
  "Hello" should "have tests" in {
    true should equal (true)
  }
}
