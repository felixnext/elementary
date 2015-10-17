import org.scalatest._
import org.scalatest.concurrent.{ ScalaFutures, AsyncAssertions, PatienceConfiguration }
import org.scalatest.time.{Millis, Seconds, Span}
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

import elementary.util.data._

class DBpediaIndexSpec extends FlatSpec with Matchers with BeforeAndAfter with ScalaFutures {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.language.postfixOps

  /*after {
    Future {
      Thread.sleep(30000)
      db.close()
      corpora.close
    }
  }*/

  implicit val defaultPatience = PatienceConfig(timeout = Span(30, Seconds), interval = Span(1, Seconds))

  "DBpedia Index" should "search for possible entities" in {
    val res1 = DBpediaIndex.searchEntity("Jarvis Iron Man")
    whenReady(res1) { result =>
      result.map(x => x._1) should contain ("http://dbpedia.org/resource/Edwin_Jarvis")
    }
  }
}
