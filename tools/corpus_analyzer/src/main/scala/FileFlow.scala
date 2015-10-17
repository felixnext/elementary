package elementary.tools.corpusanalyzer

import akka.actor.{ActorSystem}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Source, Sink, FlowGraph, Broadcast, Merge, Flow, RunnableGraph, Keep}
import akka.util.ByteString
import com.mfglabs.stream._
import com.mfglabs.stream.extensions.elasticsearch._
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }
import scalaz.Scalaz._
import elementary.util.machinelearning.MLTools
import elementary.util.machinelearning.LanguageFeatures._

object FileFlow extends FlowHelper {
  import scala.concurrent.ExecutionContext.Implicits.global

  // Holds the instance of the language features
  lazy val features = new MLTools()

  // create the source and sinks of the stream
  lazy val serializing = Flow[Map[(String, String), Int]].map(m => m.foldLeft("")( (line, t) => s"$line${t._1};${t._2}"))
  lazy val sinkIgnore = Sink.fold[Unit, EsDocument](Unit)((_,_) => Unit)

  // Defines a graph that extracts general word features
  def gWord(file: String)(implicit mat: Materializer) = Sink() { implicit builder =>
    import FlowGraph.Implicits._

    val bcast = builder.add(Broadcast[EsDocument](2))
    val wcount = Flow[EsDocument].map(doc => features.wordCount(doc.text).foldLeft("")( (line, tpl) => line + tpl._1 + ";" + tpl._2 + "\n"))
    val count = Flow[EsDocument].map(doc => "1")
    val sink1 = FlowData.createFileSink(file + "_count.csv")
    val sink2 = FlowData.createFileSink(file + "_word-count.csv")

    bcast ~> count ~> sink1
    bcast ~> wcount ~> sink2

    // return the input shape
    bcast.in
  }

  // Defines a graph that collects meta info about the docs
  def gMeta(file: String)(implicit mat: Materializer) = Sink(){ implicit builder =>
    import FlowGraph.Implicits._

    val bcast = builder.add(Broadcast[EsDocument](2))
    val lcount = Flow[EsDocument].map(doc => s"${doc.lecture};1")
    val tcount = Flow[EsDocument].map(doc => s"${doc.topic};1")
    val sink1 = FlowData.createFileSink(file + "_lecture.csv")
    val sink2 = FlowData.createFileSink(file + "_topic.csv")

    bcast ~> lcount ~> sink1
    bcast ~> tcount ~> sink2

    // return the input shape
    bcast.in
  }

  // Defines a graph that calculates NER features
  def gNer(file: String)(implicit mat: Materializer) = Sink(){ implicit builder =>
    import FlowGraph.Implicits._

    val bcast1 = builder.add(Broadcast[EsDocument](2))
    val bcast2 = builder.add(Broadcast[IndexedSeq[String]](3))
    val tokenize   = Flow[EsDocument].map(d => AnalysisFct.tokenizeText(d.text))
    val nerBreeze  = Flow[IndexedSeq[String]].map(AnalysisFct.searchBreezeNER(_))
    val nerDBpedia = Flow[IndexedSeq[String]].map(AnalysisFct.searchDBpediaNER(_))
    val posBreeze  = Flow[IndexedSeq[String]].map(AnalysisFct.searchBreezePOS(_))
    val sentences  = Flow[EsDocument].map(doc => AnalysisFct.calcSentenceLength(doc.text).foldLeft("")( (line, t) => s"$line${t._1};${t._2}\n"))
    val sink1 = FlowData.createFileSink(file + "_ner-breeze.csv")
    val sink2 = FlowData.createFileSink(file + "_ner-dbpedia.csv")
    val sink3 = FlowData.createFileSink(file + "_pos.csv")
    val sink4 = FlowData.createFileSink(file + "_sentence.csv")

    bcast1 ~> tokenize ~> bcast2 ~> nerBreeze ~> serializing ~> sink1
                          bcast2 ~> nerDBpedia ~> serializing ~> sink2
                          bcast2 ~> posBreeze ~> serializing ~> sink3
    bcast1 ~> sentences ~> sink4

    // return the input shape
    bcast1.in
  }

  // Defines a graph that calculated word coocurrence
  def gCooc(file: String)(implicit mat: Materializer) = Sink() { implicit builder =>
    import FlowGraph.Implicits._

    val bcast = builder.add(Broadcast[EsDocument](2))
    val coocWord = Flow[EsDocument].map(doc => features.wordCoocWord(doc.text))
    val coocSentence = Flow[EsDocument].map(doc => features.wordCoocSentence(doc.text))
    val sink1 = FlowData.createFileSink(file + "_cooc-word.csv")
    val sink2 = FlowData.createFileSink(file + "_cooc-sentence.csv")

    bcast ~> coocWord ~> serializing ~> sink1
    bcast ~> coocSentence ~> serializing ~> sink2

    // return the input shape
    bcast.in
  }

  // Splits the input signal and attaches the sinks
  def splitter(source: Source[String, Unit], file: String)(implicit mat: Materializer): RunnableGraph[Future[Unit]] =
  FlowGraph.closed(sinkIgnore){ implicit builder => (ignore) =>
    import FlowGraph.Implicits._

    // basic elements of the flow
    val bcast = builder.add(Broadcast[EsDocument](5))
    val convert = Flow[String].map(AnalysisFct.convertEs(_))

    source ~> convert ~> bcast
                         bcast ~> gWord(file)
                         bcast ~> gMeta(file)
                         bcast ~> gNer(file)
                         bcast ~> gCooc(file)
                         bcast ~> ignore
  }

  // starts the analysis process
  def analyze(source: Source[String, Unit], file: String)(implicit mat: Materializer): Future[EsStats] = {
    val fut = splitter(source, file).run()
    val promise = Promise[EsStats]()
    fut onComplete {
      case Success(_) =>
        reduce(file) onComplete {
          case Success(_) => promise.success(load(file))
          case Failure(e) => promise.failure(e)
        }
      case Failure(e) => promise.failure(e)
    }
    promise.future
  }

  // reduces all files
  def reduce(file: String): Future[Unit] = Future {
    Unit
  }

  def reduceKeyVal(file: String): Try[Unit] = {
    // TODO reduce
    val fileLines = Try(io.Source.fromFile(file).getLines.toList)
    //fileLines.map(file => file.map(line => line.))
    ???
  }

  // loads the EsStat from file
  def load(file: String): EsStats = {
    // TODO load file here
    ???
  }
}
