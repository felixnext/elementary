package elementary.tools.corpusanalyzer

import akka.actor.{ActorSystem}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Source, Sink, FlowGraph, Broadcast, Merge, Flow, RunnableGraph, Keep}
import akka.util.ByteString
import com.mfglabs.stream._
import com.mfglabs.stream.extensions.elasticsearch._
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import com.typesafe.config.ConfigFactory
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import scalaz.Scalaz._

import elementary.util.machinelearning.LanguageFeatures

case class EsDocument(text: String, lecture: String, topic: String)
case class EsAnalysis(count: Int, wordCount: Int, words: Map[String, Int], lectures: Map[String, Int],
  topics: Map[String, Int], nerBreeze: Map[(String, String), Int], nerDBpedia: Map[(String, String), Int],
  coocWords: Map[(String, String), Int], coocSentences: Map[(String, String), Int],
  pos: Map[(String, String), Int], sentences: Map[Int, Int])
case class EsStats(count: Int, wordCount: Int, words: Int, lectures: Int, topics: Int, sentenceLength: Double)

trait FlowHelper {
  def analyze(source: Source[String, Unit], file: String)(implicit mat: Materializer): Future[EsStats]
}

class AnalysisFlow(config: AnalysisConfiguration) {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.language.postfixOps

  // create the materializer and actor system for the stream
  implicit val system = ActorSystem("CorpusAnalyzer")
  implicit val materializer = ActorMaterializer()

  // starts the stream holding the data
  def start(): Future[EsStats] = {
    // some functions that might be used
    val completePromise = Promise[Unit]()

    val source = FlowData.createEsSource(completePromise.future)

    // execute the graph
    val res: Future[EsStats] = DBFlow.analyze(source, config.output)
    //val res: Future[EsStats] = FoldFlow.analyze(source, config.output)

    // close client connection if the stream is completed
    res onComplete {
      case _ => completePromise.success(())
    }

    res
  }

  // shutdown the actor system after completion
  def close() = {
    system.shutdown()
  }
}
