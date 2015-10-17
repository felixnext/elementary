import org.scalatest._
import org.scalatest.concurrent.{ ScalaFutures, AsyncAssertions, PatienceConfiguration }
import org.scalatest.time.{Millis, Seconds, Span}
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

import elementary.util.data._

class DBpediaSpotlightSpec extends FlatSpec with Matchers with BeforeAndAfter with ScalaFutures {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.language.postfixOps

  implicit val defaultPatience = PatienceConfig(timeout = Span(30, Seconds), interval = Span(1, Seconds))

  "DBpedia Spotlight" should "map entities in a text" in {
    val res1 = DBpediaSpotlight.search("Barack Obama, I do not like him!")
    whenReady(res1) { result =>
      result.map(_.uri) should contain ("<http://dbpedia.org/resource/Barack_Obama>")
    }
  }
}
