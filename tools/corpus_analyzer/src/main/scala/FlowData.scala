package elementary.tools.corpusanalyzer

import akka.stream.Materializer
import akka.stream.scaladsl.{Source, Sink, FlowGraph, Broadcast, Merge, Flow, RunnableGraph, Keep}
import akka.stream.io.SynchronousFileSink
import akka.util.ByteString
import com.mfglabs.stream._
import com.mfglabs.stream.extensions.elasticsearch._
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import scala.concurrent.Future

object FlowData {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.language.postfixOps

  // create a source from the elastic search
  def createEsSource(promise: Future[Unit])(implicit mat: Materializer): Source[String, Unit] = {
    // create a client to connect to elastic search
    val tconfig = ConfigFactory.load()
    implicit val client = new TransportClient(ImmutableSettings.builder.build)
    client.addTransportAddress(new InetSocketTransportAddress(
      tconfig.getString("elementary.util.data.elasticsearch.address"),
      tconfig.getInt("elementary.util.data.elasticsearch.port")
    ))
    implicit val blockingEc = ExecutionContextForBlockingOps(scala.concurrent.ExecutionContext.Implicits.global)

    promise onComplete {
      case _ => client.close()
    }

    EsStream.queryAsStream(QueryBuilders.matchAllQuery(), "basecorpus", "doc", 5 minutes, 5)
  }

  // creates a Sink that writes strings to file
  lazy val convByte = Flow[String].map(_ + "\n").map(ByteString(_))
  def createFileSink(file: String)(implicit mat: Materializer): Sink[String, Unit] = {
    Sink() { implicit builder =>
      import FlowGraph.Implicits._
      import java.io.File

      val bcast = builder.add(Broadcast[String](1))

      bcast ~> convByte ~> SynchronousFileSink(new File(file))

      bcast.in
    }
  }

  // prints data to output
  def writeOutput(data: EsAnalysis, file: String) = {
    import java.io._
    // save to csv
    val writer = new PrintWriter(new File(file))
    writer.write(s"# Documents;${data.count}\n")
    writer.write(s"# Words;${data.wordCount}\n")
    writer.write(s"# Unique Words;${data.words.size}\n")
    writer.write("\nDepartment;count\n")
    data.lectures.foreach( t => writer.write(s"${t._1};${t._2}\n") )
    writer.write("\nmodule;count\n")
    data.topics.foreach( t => writer.write(s"${t._1};${t._2}\n") )
    writer.write("\nSentence length;count\n")
    data.sentences.foreach( t => writer.write(s"${t._1};${t._2}\n") )
    writer.write("\nBreeze NER;count\n")
    data.nerBreeze.foreach( t => writer.write(s"${t._1};${t._2}\n") )
    writer.write("\nDBpedia NER;count\n")
    data.nerDBpedia.foreach( t => writer.write(s"${t._1};${t._2}\n") )
    writer.write("\nBreeze POS;count\n")
    data.pos.foreach( t => writer.write(s"${t._1};${t._2}\n") )
    writer.write("\nWord Pair;count\n")
    data.coocWords.foreach( t => writer.write(s"${t._1};${t._2}\n") )
    writer.write("\nSentence Pair;count\n")
    data.coocSentences.foreach( t => writer.write(s"${t._1};${t._2}\n") )
    writer.write("\nword;frequency;count\n")
    data.words.foreach( t => writer.write(s"${t._1};${t._2.toDouble/data.wordCount.toDouble};${t._2}\n") )
    writer.close()
  }
}
