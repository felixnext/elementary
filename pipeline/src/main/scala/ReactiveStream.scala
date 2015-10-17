package elementary.pipeline

import akka.actor.{Actor, ActorRef, ActorSelection, ActorLogging, Props}
import akka.stream.actor.{ActorPublisher}
import com.typesafe.config.ConfigFactory
import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import elementary.util.logging.ReportActor
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Success, Failure}

//! Actor that handles outgoing messages from the stream
class SinkActor(awaittime: Int, minranking: Double, worker: ActorRef) extends Actor with ActorLogging {
  import elementary.util.cluster.ClusterProtocol._
  import scala.language.postfixOps

  val reporter = context.actorOf(ReportActor.props, "reporter")
  val timeout = if (awaittime <= 0) Duration.Inf else (awaittime seconds)

  def receive = {
    case ans: AnswerRef => {
      Await.ready(ans.answer.zip(ans.candidates), timeout).value match {
        case Some(Success( (Some(data), candidates) )) =>
          reporter ! AnswerReportMessage(ans.id, true, data.answer, data.ranking, candidates, System.currentTimeMillis)
          ans.sender ! Answer(ans.id, data.answer)
        case Some(Success( (None, candidates) )) =>
          reporter ! AnswerReportMessage(ans.id, false, "[NO ANSWER]", 1.0, candidates, System.currentTimeMillis)
          ans.sender ! AnswerUnkown(ans.id)
        case Some(Failure(e)) =>
          reporter ! AnswerReportMessage(ans.id, false, "[FAILED]", 0.0, List(), System.currentTimeMillis)
          ans.sender ! NoAnswer(ans.id)
        case _ =>
          reporter ! AnswerReportMessage(ans.id, false, "[TIMEOUT]", 0.0, List(), System.currentTimeMillis)
          ans.sender ! NoAnswer(ans.id)
      }
      worker ! WorkComplete(ans)
    }
    case x => log.error("Pipeline Sink got invalid message: {}", x)
  }
}

object SinkActor {
  def props(worker: ActorRef): Props = {
    val config = ConfigFactory.load()
    Props(classOf[SinkActor], config.getInt("elementary.pipeline.answer-time"), config.getDouble("elementary.pipeline.min-ranking"), worker)
  }
}

//! Defines an Actor that is a source for the pipeline stream
class SourceActor(bufferSize: Int, master: ActorSelection) extends ActorPublisher[QueryRef] with ActorLogging {
  import akka.stream.actor.ActorPublisherMessage._

  // Defines the buffer for this actor
  var buf = Vector.empty[QueryRef]

  def receive = {
    // incoming processing messages
    case q: QueryRef if buf.size == bufferSize =>
      log.error("Dropped question with id {}", q.id)
      master ! QuestionDenied(q.id)
      q.sender ! QuestionDenied(q.id)
    case q: QueryRef =>
      if (buf.isEmpty && totalDemand > 0)
        onNext(q)
      else {
        buf :+= q
        deliverBuf()
      }

    // stream actor messages
    case Request(_) =>
      deliverBuf()
    case Cancel =>
      context.stop(self)
  }

  // check if messages from the buffer can be processed
  @annotation.tailrec
  final def deliverBuf(): Unit = {
    def push(max: Int) = {
      val (use, keep) = buf.splitAt(max)
      buf = keep
      use foreach onNext
    }

    if (totalDemand > 0) {
      if (totalDemand <= Int.MaxValue) push(totalDemand.toInt)
      else {
        push(Int.MaxValue)
        deliverBuf()
      }
    }
  }
}

object SourceActor {
  def props(master: ActorSelection): Props = {
    val config = ConfigFactory.load()
    Props(classOf[SourceActor], config.getInt("elementary.pipeline.source-buffer"), master)
  }
}
