import org.scalatest._
import org.scalatest.concurrent.{ ScalaFutures, AsyncAssertions, PatienceConfiguration }
import org.scalatest.time.{Millis, Seconds, Span}
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

import reactivemongo.api._
import reactivemongo.bson._

import elementary.util.data._

class ProcessDataSpec extends FlatSpec with Matchers with BeforeAndAfter with ScalaFutures {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.language.postfixOps

  // holds the test data that is inserted into the mongo
  val tId1: Long = 1433595428400L
  val tQuest1: String = "Why?"
  val tSender1: String = "Ms. Foo"
  val tDetails1: String = """{"corpus": "sample", "id": 20, "time":100, "details":"hello world"}"""
  val tCTime1: Long = 1433595428401L
  val tNode1: String = "Node1"
  val tNTime1: Long = 1433595428402L
  val tS1Name1: String = "process"
  val tS1Json1: String = "{S1: yay}"
  val tS1Time1: Long = 1433595428403L
  val tAnswer1: String = "Because"
  val tRanking1: Double = 0.5
  val tCandidates1 = List(("of", 0.1), ("nope", 0.05))
  val tFTime1: Long = 1433595428404L
  val tTimes1 = Map("create" -> tCTime1, "node" -> tNTime1, tS1Name1 -> tS1Time1, "finish" -> tFTime1)
  val tSteps1 = Map(tS1Name1 -> tS1Json1)
  val tFinish1 = true
  val doc1 = ProcessData(tId1, Some(tQuest1), Some(tSender1), Some(tNode1), ProcessDataDetails(Some("sample"), Some(20L), Some(100L), Some("hello world")), Some(tTimes1), Some(tSteps1), Some(tAnswer1), Some(tRanking1), Some(tFinish1), Some(tCandidates1))

  val tId2: Long = 1433595428500L
  val tNode2: String = "Node2"
  val tNTime2: Long = 1433595428502L
  val tS1Name2: String = "process"
  val tS1Json2: String = "{S1: yay}"
  val tS1Time2: Long = 1433595428403L
  val tS2Name2: String = "process"
  val tS2Json2: String = "{S1: yay}"
  val tS2Time2: Long = 1433595428403L
  val tTimes2 = Map("node" -> tNTime2, tS1Name2 -> tS1Time2, tS2Name2 -> tS2Time2)
  val tSteps2 = Map(tS1Name2 -> tS1Json2, tS2Name2 -> tS2Json2)
  val doc2 = ProcessData(tId2, None, None, Some(tNode2), ProcessDataDetails(None, None, None, None), Some(tTimes2), Some(tSteps2), None, None, None, None)

  /*after {
    Future {
      Thread.sleep(30000)
      db.close()
      corpora.close
    }
  }*/

  implicit val defaultPatience = PatienceConfig(timeout = Span(30, Seconds), interval = Span(1, Seconds))

  "ProcessData Facade" should "create a new doc" in {
    val res1 = ProcessDataCreator.createDoc(tId1)
    whenReady(res1) { result =>
      result shouldBe (true)
    }
  }

  it should "verify that doc exists" in {
    val res1 = ProcessDataCreator.exists(tId1)
    whenReady(res1) { result =>
      result shouldBe (true)
    }
    val res2 = ProcessDataCreator.exists(tId2)
    whenReady(res2) { result =>
      result shouldBe (false)
    }
  }

  it should "create doc with basic info" in {
    val res1 = ProcessDataCreator.appendCreate(tId1, tQuest1, tSender1, tDetails1, tCTime1)
    whenReady(res1) { result =>
      result shouldBe (true)
    }
  }

  it should "create doc with cluster info" in {
    val res1 = ProcessDataCreator.appendCluster(tId1, tNode1, tNTime1)
    whenReady(res1) { result =>
      result shouldBe (true)
    }
  }

  it should "create doc with process info" in {
    val res1 = ProcessDataCreator.appendProcess(tId1, tS1Name1, tS1Json1, tS1Time1)
    whenReady(res1) { result =>
      result shouldBe (true)
    }
  }

  it should "create doc with final info" in {
    val res1 = ProcessDataCreator.appendFinish(tId1, tFinish1, tAnswer1, tRanking1, tCandidates1, tFTime1)
    whenReady(res1) { result =>
      result shouldBe (true)
    }
  }

  it should "create complete doc" in {
    val res1 = ProcessDataCreator.appendCluster(tId2, tNode2, tNTime2)
    whenReady(res1) { result =>
      result shouldBe (true)
    }
    val res2 = ProcessDataCreator.appendProcess(tId2, tS1Name2, tS1Json2, tS1Time2)
    whenReady(res2) { result =>
      result shouldBe (true)
    }
    val res3 = ProcessDataCreator.appendProcess(tId2, tS2Name2, tS2Json2, tS2Time2)
    whenReady(res3) { result =>
      result shouldBe (true)
    }
    val res4 = ProcessDataCreator.exists(tId2)
    whenReady(res4) { result =>
      result shouldBe (true)
    }
  }

  it should "count all docs" in {
    val count = ProcessDataMerger.count
    whenReady(count) { c =>
      c should be > 0L
    }
  }

  it should "get a specific doc" in {
    val res1 = ProcessDataMerger.get(tId1)
    whenReady(res1) { result =>
      result shouldBe (Some(doc1))
    }
    val res2 = ProcessDataMerger.get(tId2)
    whenReady(res2) { result =>
      result shouldBe (Some(doc2))
    }
  }

  it should "list all docs (OPTIONAL)" in {
    val lsFut = ProcessDataMerger.list()
    whenReady(lsFut) { ls =>
      ls should contain (doc1)
    }
  }

  it should "delete all created docs [clean-up]" in {
    val res1 = ProcessDataCreator.mongo.remove(BSONDocument("id" -> tId1))
    Await.ready(res1, 60 seconds)
    val res2 = ProcessDataCreator.mongo.remove(BSONDocument("id" -> tId2))
    Await.ready(res2, 60 seconds)
    val res3 = ProcessDataCreator.exists(tId1)
    whenReady(res3) { result =>
      result shouldBe (false)
    }
    val res4 = ProcessDataCreator.exists(tId2)
    whenReady(res4) { result =>
      result shouldBe (false)
    }
  }

  it should "get newest elements" in {
    val futOpt = ProcessDataMerger.newest(10)
    whenReady(futOpt) { result =>
      result.size shouldBe (10)
    }
  }
}
