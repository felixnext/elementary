package elementary.tools.corpusanalyzer

import akka.actor.{ActorSystem}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Source, Sink, FlowGraph, Broadcast, Merge, Flow, RunnableGraph, Keep}
import akka.util.ByteString
import com.mfglabs.stream._
import com.mfglabs.stream.extensions.elasticsearch._
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import scalaz.Scalaz._
import elementary.util.machinelearning.MLTools
import elementary.util.machinelearning.LanguageFeatures._

object FoldFlow extends FlowHelper {
  import scala.concurrent.ExecutionContext.Implicits.global

  // Holds the instance of the language features
  lazy val features = new MLTools()

  // create the source and sinks of the stream
  lazy val sinkCount = Sink.fold[Int, EsDocument](0)((value, doc) => value + 1)
  lazy val sinkMapCount = Sink.fold[Map[String, Int], Map[String, Int]](Map())( _ |+| _ )  // scalaZ operator to merge maps
  lazy val sinkMapTplCount = Sink.fold[Map[(String, String), Int], Map[(String, String), Int]](Map())( _ |+| _ )  // scalaZ operator to merge maps
  lazy val sinkMapIntCount = Sink.fold[Map[Int, Int], Map[Int, Int]](Map())( _ |+| _ )

  // Defines a graph that extracts general word features
  def gWord(implicit mat: Materializer) = Sink(sinkCount, sinkMapCount)((fCount, fWord) => (fCount |@| fWord){(_,_)})
  { implicit builder => (count, word) =>
    import FlowGraph.Implicits._

    val bcast = builder.add(Broadcast[EsDocument](2))
    val wcount = Flow[EsDocument].map(doc => features.wordCount(doc.text))

    bcast ~> count
    bcast ~> wcount ~> word.inlet

    // return the input shape
    bcast.in
  }

  // Defines a graph that collects meta info about the docs
  def gMeta(implicit mat: Materializer) = Sink(sinkMapCount, sinkMapCount)((fLecture, fTopic) => (fLecture |@| fTopic){(_,_)})
  { implicit builder => (lecture, topic) =>
    import FlowGraph.Implicits._

    val bcast = builder.add(Broadcast[EsDocument](2))
    val lcount = Flow[EsDocument].map(doc => Map(doc.lecture -> 1))
    val tcount = Flow[EsDocument].map(doc => Map(doc.topic -> 1))

    bcast ~> lcount ~> lecture.inlet
    bcast ~> tcount ~> topic.inlet

    // return the input shape
    bcast.in
  }

  // Defines a graph that calculates NER features
  def gNer(implicit mat: Materializer) = Sink(sinkMapTplCount,sinkMapTplCount,sinkMapTplCount,sinkMapIntCount)
  { (fBreeze,fDBpedia,fPos,fSent) => (fBreeze |@| fDBpedia |@| fPos |@| fSent){(_,_,_,_)} }
  { implicit builder => (breeze, dbpedia, pos, sentence) =>
    import FlowGraph.Implicits._

    val bcast1 = builder.add(Broadcast[EsDocument](2))
    val bcast2 = builder.add(Broadcast[IndexedSeq[String]](3))
    val tokenize   = Flow[EsDocument].map(d => AnalysisFct.tokenizeText(d.text))
    val nerBreeze  = Flow[IndexedSeq[String]].map(AnalysisFct.searchBreezeNER(_))
    val nerDBpedia = Flow[IndexedSeq[String]].map(AnalysisFct.searchDBpediaNER(_))
    val posBreeze  = Flow[IndexedSeq[String]].map(AnalysisFct.searchBreezePOS(_))
    val sentences  = Flow[EsDocument].map(doc => AnalysisFct.calcSentenceLength(doc.text))

    bcast1 ~> tokenize ~> bcast2 ~> nerBreeze ~> breeze.inlet
                          bcast2 ~> nerDBpedia ~> dbpedia.inlet
                          bcast2 ~> posBreeze ~> pos.inlet
    bcast1 ~> sentences ~> sentence.inlet

    // return the input shape
    bcast1.in
  }

  // Defines a graph that calculated word coocurrence
  def gCooc(implicit mat: Materializer) = Sink(sinkMapTplCount, sinkMapTplCount)((fWord, fSentence) => (fWord |@| fSentence){(_,_)})
  { implicit builder => (word, sentence) =>
    import FlowGraph.Implicits._

    val bcast = builder.add(Broadcast[EsDocument](2))
    val coocWord = Flow[EsDocument].map(doc => features.wordCoocWord(doc.text))
    val coocSentence = Flow[EsDocument].map(doc => features.wordCoocSentence(doc.text))

    bcast ~> coocWord ~> word.inlet
    bcast ~> coocSentence ~> sentence.inlet

    // return the input shape
    bcast.in
  }

  // Splits the input signal and attaches the sinks
  def splitter(source: Source[String, Unit])(implicit mat: Materializer): RunnableGraph[Future[EsAnalysis]] =
  FlowGraph.closed(gWord, gMeta, gNer, gCooc)
    { (fWord, fMeta, fNer, fCooc) => (fWord |@| fMeta |@| fNer |@| fCooc)
      { (word, meta, ner, cooc) =>
        EsAnalysis(word._1,word._2.foldLeft(0)(_ + _._2),word._2,meta._1,meta._2,ner._1,ner._2,cooc._1,cooc._2,ner._3,ner._4)
      }
    }
  { implicit builder => (word, meta, ner, cooc) =>
    import FlowGraph.Implicits._

    // basic elements of the flow
    val bcast = builder.add(Broadcast[EsDocument](4))
    val convert = Flow[String].map(AnalysisFct.convertEs(_))

    source ~> convert ~> bcast ~> word.inlet
                         bcast ~> meta.inlet
                         bcast ~> ner.inlet
                         bcast ~> cooc.inlet
  }

  def analyze(source: Source[String, Unit], file: String)(implicit mat: Materializer): Future[EsStats] = {
    val fut = splitter(source).run()
    fut onComplete {
      case Success(data) => FlowData.writeOutput(data, file + ".csv")
      case Failure(e) => // Nothing
    }
    // TODO calculate average
    fut.map(d => EsStats(d.count, d.wordCount, d.words.size, d.lectures.size, d.topics.size, 0.0))
  }
}
