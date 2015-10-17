package elementary.api

import akka.actor.{Actor, ActorRef, ActorLogging}
import akka.agent.Agent
import akka.pattern.pipe
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri, HttpEntity, MediaTypes}
import elementary.util.common.Communication._
import elementary.util.logging.ReportActor
import scala.collection.mutable.Map
import scala.concurrent.{Future, Promise, Await}
import scala.concurrent.duration._

//! Handles connections to the cluster backend while holding a promise of the result
class PromiseActor(frontend: ActorRef) extends Actor with ActorLogging {
  import scala.concurrent.ExecutionContext.Implicits.global

  // create the report actor for input messages on the system
  val reporter = context.actorOf(ReportActor.props)

  // Holds the list of questions that are used to
  val questions = Map.empty[Long, Promise[AnswerMessage]]

  def receive = {
    case QuestionRequest(query, tasker, details) =>
      //val id = Await.result(idAgent.future, timeout.duration)
      val id = System.currentTimeMillis
      reporter ! QuestionReportMessage(id, query, tasker, details, System.currentTimeMillis)
      // create a promise for the answer
      val promise = Promise[AnswerMessage]()
      questions += (id -> promise)
      // send of to cluster and return future
      frontend ! Question(id, query)
      pipe(promise.future) to sender()
      // avoid duplicate id's
      if (System.currentTimeMillis == id)
        Thread.sleep(1)
    case ans: AnswerMessage =>
      if (questions.contains(ans.id)) {
        log.info("Got answer for question: [{}]", ans.id)
        val promise = questions(ans.id)
        promise.success(ans)
        questions -= ans.id
      }
      else
        log.error("Got unkown answer: [{}]", ans)
  }
}
