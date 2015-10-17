//import akka.event.NoLogging
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Flow
import org.scalatest._
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import slick.driver.MySQLDriver.api._
import slick.driver.MySQLDriver.backend.Database
//import kamon.Kamon

import elementary.statistic.views.{CorporaRoutes, CorporaProtocols}
import elementary.statistic.models.{Tables, CorporaHull, CorpusInfo, QuestionHull, CorporaInfo, QuestionInfo}

class QuestionCorporaRESTSpec extends FlatSpec with Matchers with BeforeAndAfter with ScalatestRouteTest with CorporaProtocols {
  import scala.language.postfixOps

  // define some basic setups
  override def testConfigSource = "akka.loglevel = WARNING"

  // create link to db to clean up
  val db = Database.forConfig("elementary.util.data.questiondb")

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
  //val testQ1Data = QuestionData(1, testQ1, testQ1qtype, testQ1atype, testQ1answers, false)
  //val testQ2Data = QuestionData(2, testQ2, testQ2qtype, testQ2atype, testQ2answers, false)

  after {
    Future {
      Thread.sleep(10000)
      db.close()
    }
  }

  "Question Corpora API" should "create a new question corpus and avoid duplicates" in {
    // delete old data
    val q = Tables.corpora.filter(_.name === testCorpus)
    val action = q.delete
    Await.ready(db.run(action), 60 seconds)

    // run test routes
    Post(s"/corpora/create", CorpusInfo(testCorpus, testDesc)) ~> CorporaRoutes.route ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[String] shouldBe s"""{"success": true, "operation": "create", "name": "$testCorpus"}"""
    }
    Post(s"/corpora/create", CorpusInfo(testCorpus, testDesc)) ~> CorporaRoutes.route ~> check {
      status shouldBe Conflict
      contentType shouldBe `application/json`
      responseAs[String] shouldBe s"""{"success": false, "operation": "create", "name": "$testCorpus", "error": "duplicate"}"""
    }
  }

  it should "list all existing corpora" in {
    Get(s"/corpora") ~> CorporaRoutes.route ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[List[CorporaHull]] should contain (CorporaHull(testCorpus, testDesc, false))
    }
  }

  it should "create a question for a corpus" in {
    // delete all questions for that corpus
    val q = Tables.questions.filter(_.corpus === testCorpus)
    val action = q.delete
    Await.ready(db.run(action), 60 seconds)

    // create new questions
    Post(s"/corpora/$testCorpus/question/create", QuestionHull(testQ1, testQ1qtype, testQ1atype, testQ1answers)) ~> CorporaRoutes.route ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[String] should startWith (s"""{"success": true, "operation": "question/create", "name": "$testCorpus", "id":""")
    }
    Post(s"/corpora/$testCorpus/question/create", QuestionHull(testQ2, testQ2qtype, testQ2atype, testQ2answers)) ~> CorporaRoutes.route ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[String] should startWith (s"""{"success": true, "operation": "question/create", "name": "$testCorpus", "id":""")
    }
  }

  it should "retrieve a single corpus" in {
    Get(s"/corpora/$testCorpus") ~> CorporaRoutes.route ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      val data = responseAs[CorporaInfo]
      data.name shouldBe testCorpus
      data.description shouldBe testDesc
      data.questions.size shouldBe 2
    }
  }

  it should "deprecate a single corpus" in {
    Get(s"/corpora/$testCorpus/deprecate") ~> CorporaRoutes.route ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[String] shouldBe s"""{"success": true, "operation": "deprecate", "name": "$testCorpus"}"""
    }
    Get(s"/corpora/$testCorpus") ~> CorporaRoutes.route ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[CorporaInfo].deprecated shouldBe true
    }
  }

  it should "deprecate a question" in {
    val query = for { c <- Tables.questions if c.corpus === testCorpus && c.question === testQ1 } yield (c.id)
    val f = db.run(query.result).map(seq => seq.head)
    val qid1 = Await.ready(f, 60 seconds).value.get.getOrElse(0)

    Get(s"/corpora/$testCorpus/question/$qid1/deprecate") ~> CorporaRoutes.route ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[String] shouldBe s"""{"success": true, "operation": "question/deprecate", "name": "$testCorpus", "id": $qid1}"""
    }
    Get(s"/corpora/$testCorpus") ~> CorporaRoutes.route ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[CorporaInfo].questions should contain (QuestionInfo(qid1.toLong, testQ1, testQ1qtype, testQ1atype, testQ1answers, true))
    }
  }
}
