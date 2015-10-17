package elementary.tools.answerscrawler

import scala.util.{Success, Failure}
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

import reactivemongo.api._
import reactivemongo.bson._
import elementary.util.data.MongoFacade

import akka.actor._
import akka.stream.actor._
import akka.stream.scaladsl._
import akka.stream.ActorMaterializer

import net.ruippeixotog.scalascraper.browser.Browser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import org.jsoup.nodes.Element

case class Site(url: String, html: Future[String])
case class Question(id: Option[String], url: String, html: String, question: String, questiondetail: Option[String],
  category: Option[List[String]], bestanswer: Option[String], otheranswers: Option[List[String]])

object AnswersCrawler extends App with MongoFacade {
  implicit val system = ActorSystem("test")
  implicit val materializer = ActorMaterializer()

  override val mongoDBName = "YahooAnswers"
  override val mongoCollName = "Questions"

  val actorRef = system.actorOf(Props[UrlsSource])
  val urls = ActorPublisher[String](actorRef)
  val browser = new Browser

  val seeds = List("question/index?qid=20140122224920AAYBin4",
                   "question/index?qid=20140219103831AAm7rw5",
                   "question/index?qid=20140123131509AAiAynT",
                   "question/index?qid=20140220230914AAjjctn"
                   )

  val g = FlowGraph.closed() { implicit builder: FlowGraph.Builder[Unit] =>
    import FlowGraph.Implicits._

    val source = Source(urls)

    val sink = Sink.foreach[Future[Option[Question]]](q => {
      import scala.concurrent.ExecutionContext.Implicits.global
      q.map {
        case Some(q) => saveQuestion(q)
        case None => None
      }
    })

    val urlsadd = Sink.foreach[Future[List[String]]]( l => {
      import scala.concurrent.ExecutionContext.Implicits.global
      l.map { x => x.foreach(actorRef ! _)}
    })

    val crawler = Flow[String].map(url => crawl(url))
    val analyzer = Flow[Site].map(url => analyze(url))

    val unzip = builder.add(Unzip[Site, Future[List[String]]]())

    source ~> crawler ~> unzip.in

    unzip.out0 ~> analyzer ~> sink
    unzip.out1 ~> urlsadd
  }

  seeds.foreach(actorRef ! _)

  g.run()

  //get url and return document and all found urls matching a pattern
  def crawl(url: String) : (Site, Future[List[String]]) = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val response : Future[String] = Future {
      val in = scala.io.Source.fromURL("https://answers.yahoo.com/" + url, "utf8")
      val r = in.mkString
      in.close()
      r
    }

    val pattern = """(?:<a.*?href="/)([a-zA-Z/?=].*?)(?:")""".r

    val urls: Future[List[String]] = response.map { x =>
        pattern.findAllIn(x).matchData.toList map {
        m => m.group(1)
      }
    }

    (Site(url, response), urls)
  }

  //fetch
  def analyze(site: Site) : Future[Option[Question]] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    site.html map { s =>
      val html = browser.parseString(s)

      //extract question
      val question: Option[Element] = html >?> element("#ya-question-detail h1")

      question match {
        case Some(q) => {
          println("QUESTION: " + q.text)
          val id: Option[String] = html >?> attr("data-ya-question-id")("#ya-question-detail")
          val category: Option[List[String]] = html >?> elementList("#brdCrb a") >>
            text("a")
          val questiondetail: Option[String] = html >?> element("#ya-question-detail") >>
            text(".ya-q-text")
          val bestanswer: Option[String] = html >?> element("#ya-best-answer") >>
            text(".ya-q-full-text")
          val otheranswers: Option[List[String]] = html >?> elementList("#ya-qn-answers li") >>
            text(".ya-q-full-text")

          Some(Question(id, site.url, s, q.text, questiondetail, category, bestanswer, otheranswers))
        }
        case None =>
          println("NO QUESTION FOUND")
          None
      }
    }
  }

  //save
  def saveQuestion(q: Question) : Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

      //! Checks if a document with the specific id already exists
    def exists(id: Option[String]): Future[Boolean] = {
      id match {
        case Some(id) => {
          val query = BSONDocument("id" -> id)
          mongo.find(query).one[BSONDocument].map( _ match { case Some(x) => true; case None => false } )
        }
        case None =>
          Future(false)
      }
    }

    exists(q.id).onComplete {
      case Success(false) => { // Note: Docuemnt does not exist
        val document = BSONDocument(
          "id" -> q.id,
          "url" -> q.url,
          "question" -> q.question,
          "questiondetail" -> q.questiondetail,
          "category" -> q.category,
          "bestanswer" -> q.bestanswer,
          "otheranswers" -> q.otheranswers
        )

        val future = mongo.insert(document)

        future.onComplete {
          case Failure(e) => throw e
          case Success(writeResult) =>
            println("SAVED: " + q.question)
        }
      }
      case Success(true) => println("DUPLICATE: " + q.question)
      case Failure(e)    => throw e
    }
  }
}
