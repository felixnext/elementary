//import akka.event.NoLogging
import org.scalatest._
import org.scalatest.concurrent.{ ScalaFutures, AsyncAssertions, PatienceConfiguration }
import org.scalatest.time.{Millis, Seconds, Span}
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import slick.driver.MySQLDriver.api._
import slick.driver.MySQLDriver.backend.Database
//import kamon.Kamon

import elementary.statistic.models._

class CorporaDataSpec extends FlatSpec with Matchers with BeforeAndAfter with ScalaFutures {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.language.postfixOps

  // create link to db to clean up
  val db = Database.forConfig("elementary.util.data.questiondb")
  val corpora = Corpora.create

  // holds the test data that is inserted into the db
  val testCorpus = "scalatest"
  val testDesc = "a simple example corpus"
  val testQ1 = "Why is this?"
  val testQ1qtype = "why"
  val testQ1atype = "text"
  val testQ1answers = List("Because", "of")
  val testQ2 = "How do your do?"
  val testQ2qtype = "how"
  val testQ2atype = "text"
  val testQ2answers = List("Foo", "Bar")

  /*after {
    Future {
      Thread.sleep(30000)
      db.close()
      corpora.close
    }
  }*/

  implicit val defaultPatience = PatienceConfig(timeout = Span(30, Seconds), interval = Span(1, Seconds))

  "CorporaData Facade" should "create a new question corpus and avoid duplicates" in {
    // delete old data
    val q = Tables.corpora.filter(_.name === testCorpus)
    val action = q.delete
    Await.ready(db.run(action), 60 seconds)

    // create data
    val res1 = corpora.create(CorpusInfo(testCorpus, testDesc))
    whenReady(res1) { result =>
      result shouldBe ((): Unit)
    }
    val res2 = corpora.create(CorpusInfo(testCorpus, testDesc))
    whenReady(res2.failed) { ex =>
      ex shouldBe an [Exception]
    }
  }

  it should "list all existing corpora" in {
    val res1 = corpora.list
    whenReady(res1) { result =>
      result should contain (CorporaHull(testCorpus, testDesc, false))
    }
  }

  it should "create a question for a corpus" in {
    // delete all questions for that corpus
    val q = Tables.questions.filter(_.corpus === testCorpus)
    val action = q.delete
    Await.ready(db.run(action), 60 seconds)

    val res1 = corpora.addQuestion(testCorpus, QuestionHull(testQ1, testQ1qtype, testQ1atype, testQ1answers))
    whenReady(res1) { result =>
      result shouldBe > (0)
    }
    val res2 = corpora.addQuestion(testCorpus, QuestionHull(testQ2, testQ2qtype, testQ2atype, testQ2answers))
    whenReady(res2) { result =>
      result shouldBe > (0)
    }
  }

  it should "retrieve a single corpus" in {
    val res1 = corpora.get(testCorpus)
    whenReady(res1) { result =>
      result.name shouldBe testCorpus
      result.description shouldBe testDesc
      result.questions.size shouldBe 2
    }
  }

  it should "deprecate a single corpus" in {
    val res1 = corpora.deprecate(testCorpus)
    whenReady(res1) { result =>
      result shouldBe ((): Unit)
    }
    val res2 = corpora.get(testCorpus)
    whenReady(res2) { result =>
      result.deprecated shouldBe (true)
    }
  }

  it should "deprecate a question" in {
    val query = for { c <- Tables.questions if c.corpus === testCorpus && c.question === testQ1 } yield (c.id)
    val f = db.run(query.result).map(seq => seq.head)
    val qid1 = Await.ready(f, 60 seconds).value.get.getOrElse(1)

    val res1 = corpora.deprecateQuestion(testCorpus, qid1)
    whenReady(res1) { result =>
      result shouldBe ((): Unit)
    }
    val res2 = corpora.get(testCorpus)
    whenReady(res2) { result =>
      result.questions should contain (QuestionInfo(qid1.toLong, testQ1, testQ1qtype, testQ1atype, testQ1answers, true))
    }
  }
}
