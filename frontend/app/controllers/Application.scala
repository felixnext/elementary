package controllers

import scala.concurrent.{Future, Promise}
import scala.util.{Success, Failure, Random}
import scala.compat.Platform.currentTime

import org.elasticsearch.action.search.SearchResponse

import play.api._
import play.api.mvc._
import play.api.libs.ws._
import play.api.libs.json._
import play.api.data._
import play.api.data.Forms._

import models._

case class AskSubmission(question: String)

object Application extends Controller {
  import play.api.Play.current
  import scala.concurrent.ExecutionContext.Implicits.global

  val questionForm = Form(
    mapping(
      "id" -> ignored(0L),
      "question" -> nonEmptyText,
      "qtype" -> nonEmptyText,
      "atype" -> nonEmptyText,
      "answer" -> list(nonEmptyText),
      "deprecated" -> ignored(false)
    )(QuestionData.apply)(QuestionData.unapply)
  )

  val askForm = Form(
    mapping(
      "question" -> nonEmptyText
    )(AskSubmission.apply)(AskSubmission.unapply)
  )

  def index = Action { implicit request => {
      Ok(views.html.index(askForm, None, None, None))
    }
  }

  def stats(count: Int) = Action.async { implicit request => {
      //get random doc from basecorpus
      val doc : Future[List[StatisticData]] = StatisticData.getList(count)

      doc.map( data => {
        Ok(views.html.stats(data))
      })
    }
  }

  def questions = Action.async { implicit request => {
      //get random doc from basecorpus
      val seed = currentTime
      val doc : Future[BaseData] = BaseData.getRandom(seed)

      doc.map( d => {
        val offset = Random.nextInt(d.text.length-200)
        val text = getSampleText(d.text, offset)

        Ok(views.html.questions(questionForm, d, text, seed, offset))
      })
    }
  }

  def handleAsk = Action.async { implicit request =>
      askForm.bindFromRequest.fold(
      formWithErrors => {
        Future.successful(BadRequest(views.html.index(formWithErrors, None, None, None)))
      },
      askData => {
        val answer: Future[AnswerData] = AskData.get(askData.question)
        val promise = Promise[Result]()
        answer onComplete {
          case Success(data) =>
            StatisticData.getId(data.id) onComplete {
              case Success(stats) => promise.success( Ok(views.html.index(askForm, Some(askData.question + s" [${data.id}]"), Some(data.answer), Some(stats))) )
              case Failure(_) => promise.success( Ok(views.html.index(askForm, Some(askData.question + s" [${data.id}]"), Some(data.answer), None)) )
            }
          case Failure(e) => promise.success( BadRequest(views.html.index(askForm, Some(askData.question), Some("[FAILED: TIMEOUT]"), None)).flashing(
            "failure" -> "The server response timed out!"
          ) )
        }
        promise.future
      }
    )
  }

  def handleQuestion(seed: Long, offset: Int) = Action.async { implicit request =>
      questionForm.bindFromRequest.fold(
      formWithErrors => {
        // binding failure
        val doc : Future[BaseData] = BaseData.getRandom(seed)

        doc.map( d => {
          val text = getSampleText(d.text, offset)

          BadRequest(views.html.questions(formWithErrors, d, text, seed, offset))
        })
      },
      questionData => {
        // binding success
        val question = QuestionData(
          0L,
          questionData.question,
          questionData.qtype,
          questionData.atype,
          questionData.answer,
          false)
        QuestionData.put(question, "scalatest")
        Future.successful(Redirect(routes.Application.index).flashing(
          "success" -> "The question was added."
        ))
      }
    )
  }

  def getSampleText(text: String, offset: Int) : String = {
    val r = "(?:#)(.*?)(#.*?){4}".r
    r.findFirstMatchIn(text.substring(offset)).get.toString
  }
}
