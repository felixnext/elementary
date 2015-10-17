import org.scalatest._
import org.scalatest.concurrent.{ ScalaFutures, AsyncAssertions, PatienceConfiguration }
import org.scalatest.time.{Millis, Seconds, Span}
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

import elementary.util.data._

class DBpediaSpec extends FlatSpec with Matchers with BeforeAndAfter with ScalaFutures {
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

  "DBpedia" should "search for entities with specific type" in {
    val res1 = DBpedia.ofType("<http://umbel.org/umbel/rc/CartoonCharacter>", "FILTER(CONTAINS(STR(?name), \"Iron\"))")
    whenReady(res1) { result =>
      result match {
        case Some(ls) => ls should contain ("http://dbpedia.org/resource/Iron_Man")
        case _ => assert(false)
      }
    }
  }
}
