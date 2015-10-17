package elementary.tools.corpusanalyzer

import akka.actor.{ActorSystem}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Source, Sink, FlowGraph, Broadcast, Merge, Flow, RunnableGraph, Keep}
import akka.util.ByteString
import com.mfglabs.stream._
import com.mfglabs.stream.extensions.elasticsearch._
import scala.concurrent.{Future, Promise, Await}
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }
import scalaz.Scalaz._
import slick.driver.MySQLDriver.api._
import slick.driver.MySQLDriver.backend.Database
import elementary.tools.corpusanalyzer.models.Tables
import elementary.util.machinelearning.MLTools
import elementary.util.machinelearning.LanguageFeatures._

object DBFlow extends FlowHelper {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.language.postfixOps

  implicit val db = Database.forConfig("elementary.util.data.questiondb")
  lazy val features = new MLTools()
  lazy val serializing = Flow[Map[(String, String), Int]].map(m => m.foldLeft("")( (line, t) => s"$line${t._1};${t._2}"))
  lazy val sinkIgnore = Sink.fold[Unit, EsDocument](Unit)((_,_) => Unit)

  def CountDBSink(value: String)(implicit mat: Materializer): Sink[String, Future[Unit]] = {
    Sink.foreach[String](_ => {
      val query =(for {
        count <- Tables.general.filter(_.key === value).map(_.count).take(1).result
        _ <- Tables.general.insertOrUpdate(Tables.GeneralKV(value, count.headOption.getOrElse(0)+1))
      } yield ()).transactionally
      Await.ready(db.run(query), 120 seconds)
    })
  }

  def StrCountDBSink(table: TableQuery[_ <: Tables.BaseTable])(implicit mat: Materializer): Sink[String, Future[Unit]] = {
    Sink.foreach[String](str => {
      val str2 = if(str.length > 250) str.substring(0, 250) else str
      val query =(for {
        count <- table.filter(_.key === str2).map(_.count).take(1).result
        _ <- table.insertOrUpdate(Tables.GeneralKV(str2, count.headOption.getOrElse(0)+1))
      } yield ()).transactionally
      Await.ready(db.run(query), 120 seconds)
    })
  }

  def MapDBSink[B](table: TableQuery[_ <: Tables.BaseTable])(implicit mat: Materializer): Sink[Map[String, Int], Future[Unit]] = {
    Sink.foreach[Map[String, Int]](_.foreach( keyval => {
      val str2 = if(keyval._1.length > 250) keyval._1.substring(0, 250) else keyval._1
      val query =(for {
        count <- table.filter(_.key === str2).map(_.count).take(1).result
        _ <- table.insertOrUpdate(Tables.GeneralKV(str2, count.headOption.getOrElse(0)+keyval._2))
      } yield ()).transactionally
      Await.ready(db.run(query), 120 seconds)
    }))
  }

  def TplDBSink(table: TableQuery[_ <: Tables.BaseTable])(implicit mat: Materializer): Sink[Map[(String, String), Int], Future[Unit]] = {
    Sink.foreach[Map[(String, String), Int]](_.foreach( keyval => {
      val str = keyval._1.toString
      val str2 = if(str.length > 250) str.substring(0, 250) else str
      val query =(for {
        count <- table.filter(_.key === str2).map(_.count).take(1).result
        _ <- table.insertOrUpdate(Tables.GeneralKV(str2, count.headOption.getOrElse(0)+keyval._2))
      } yield ()).transactionally
      Await.ready(db.run(query), 120 seconds)
    }))
  }

  // Defines a graph that extracts general word features
  def gWord(implicit mat: Materializer) = Sink() { implicit builder => import FlowGraph.Implicits._

    val bcast = builder.add(Broadcast[EsDocument](2))
    val wcount = Flow[EsDocument].map(doc => features.wordCount(doc.text))
    val count = Flow[EsDocument].map(doc => "1")
    val sink1 = CountDBSink("docs")
    val sink2 = MapDBSink(Tables.words)

    bcast ~> count ~> sink1
    bcast ~> wcount ~> sink2
    bcast.in
  }

  // Defines a graph that collects meta info about the docs
  def gMeta(implicit mat: Materializer) = Sink(){ implicit builder => import FlowGraph.Implicits._

    val bcast = builder.add(Broadcast[EsDocument](2))
    val lcount = Flow[EsDocument].map(doc => doc.lecture)
    val tcount = Flow[EsDocument].map(doc => doc.topic)
    val sink1 = StrCountDBSink(Tables.lectures)
    val sink2 = StrCountDBSink(Tables.topics)

    bcast ~> lcount ~> sink1
    bcast ~> tcount ~> sink2
    bcast.in
  }

  // Defines a graph that calculates NER features
  def gNer(implicit mat: Materializer) = Sink(){ implicit builder => import FlowGraph.Implicits._

    val bcast1 = builder.add(Broadcast[EsDocument](2))
    val bcast2 = builder.add(Broadcast[IndexedSeq[String]](3))
    val tokenize   = Flow[EsDocument].map(d => AnalysisFct.tokenizeText(d.text))
    val nerBreeze  = Flow[IndexedSeq[String]].map(AnalysisFct.searchBreezeNER(_))
    val nerDBpedia = Flow[IndexedSeq[String]].map(AnalysisFct.searchDBpediaNER(_))
    val posBreeze  = Flow[IndexedSeq[String]].map(AnalysisFct.searchBreezePOS(_))
    val sentences  = Flow[EsDocument].map(doc => AnalysisFct.calcSentenceLength(doc.text).map(tpl => (tpl._1.toString, tpl._2)))
    val sink1 = TplDBSink(Tables.ner_breeze)
    val sink2 = TplDBSink(Tables.ner_dbp)
    val sink3 = TplDBSink(Tables.pos_breeze)
    val sink4 = MapDBSink(Tables.sentences)

    bcast1 ~> tokenize ~> bcast2 ~> nerBreeze ~> sink1
                          bcast2 ~> nerDBpedia ~> sink2
                          bcast2 ~> posBreeze ~> sink3
    bcast1 ~> sentences ~> sink4
    bcast1.in
  }

  // Defines a graph that calculated word coocurrence
  def gCooc(implicit mat: Materializer) = Sink() { implicit builder => import FlowGraph.Implicits._

    val bcast = builder.add(Broadcast[EsDocument](1))
    val coocWord = Flow[EsDocument].map(doc => features.wordCoocWord(doc.text))
    val coocSentence = Flow[EsDocument].map(doc => features.wordCoocSentence(doc.text))
    val sink1 = TplDBSink(Tables.coocwords)
    val sink2 = TplDBSink(Tables.coocsents)

    bcast ~> coocWord ~> sink1
    //bcast ~> coocSentence ~> sink2
    bcast.in
  }

  // Splits the input signal and attaches the sinks
  def splitter(source: Source[String, Unit])(implicit mat: Materializer): RunnableGraph[Future[Unit]] =
  FlowGraph.closed(sinkIgnore){ implicit builder => (ignore) => import FlowGraph.Implicits._

    val bcast = builder.add(Broadcast[EsDocument](5))
    val convert = Flow[String].map(AnalysisFct.convertEs(_))

    source ~> convert ~> bcast
                         bcast ~> gWord
                         bcast ~> gMeta
                         bcast ~> gNer
                         bcast ~> gCooc
                         bcast ~> ignore
  }

  // starts the analysis process
  def analyze(source: Source[String, Unit], file: String)(implicit mat: Materializer): Future[EsStats] = {
    val clearing = DBIO.seq(
      (Tables.coocwords.schema ++ Tables.coocsents.schema ++ Tables.sentences.schema ++ Tables.words.schema ++ Tables.general.schema).drop,
      (Tables.ner_breeze.schema ++ Tables.ner_dbp.schema ++ Tables.pos_breeze.schema ++ Tables.lectures.schema ++ Tables.topics.schema).drop
    )
    val setup = DBIO.seq(
      (Tables.coocwords.schema ++ Tables.coocsents.schema ++ Tables.sentences.schema ++ Tables.words.schema ++ Tables.general.schema).create,
      (Tables.ner_breeze.schema ++ Tables.ner_dbp.schema ++ Tables.pos_breeze.schema ++ Tables.lectures.schema ++ Tables.topics.schema).create
    )
    val promise = Promise[EsStats]()
    db.run(clearing) onComplete {
      case _ =>
        db.run(setup) onComplete {
          case Success(_) =>
            println("created DB schema")
            splitter(source).run() onComplete {
              case Success(_) =>
                val stats = load(file)
                promise.success(stats)
              case Failure(e) => promise.failure(e)
            }
          case Failure(e) =>
            println(s"Failed with exception: $e")
        }
    }
    promise.future.onComplete {
      case _ => db.close()
    }
    promise.future
  }

  // loads the EsStat from file
  def load(file: String): EsStats = {
    val query = for {
      count <- Tables.general.filter(_.key === "docs").map(_.count).take(1).result
      wordCount <- Tables.words.map(_.count).sum.result
      words <- Tables.words.length.result
      lectures <- Tables.lectures.length.result
      topics <- Tables.topics.length.result
      //sentences <- Tables.sentences.result
      //sentencesSum <- Tables.sentences.map(_.count).sum.result
    } yield EsStats(count.headOption.getOrElse(0), wordCount.getOrElse(0), words, lectures, topics, 0.0)
      //sentences.map(ls => ls.foldLeft(0)((data, sum) => sum + (data._1.toInt * data._2)).toDouble).getOrElse(0.0) /
      //  sentencesSum.map(_.toDouble).getOrElse(0))
    Await.result(db.run(query), 120 seconds)
  }
}
