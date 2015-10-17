package elementary.statistic.models

import scala.concurrent.{Future, Promise}
import scala.util.{Success, Failure}
import slick.driver.MySQLDriver.api._
import slick.driver.MySQLDriver.backend.Database

// Defines the basic data that can be used
case class CorpusInfo(name: String, description: String = "")
case class CorporaHull(name: String, description: String, deprecated: Boolean)
case class CorporaInfo(name: String, description: String, questions: List[QuestionInfo], deprecated: Boolean)
case class QuestionInfo(id: Long, question: String, qtype: String, atype: String, answer: List[String], deprecated: Boolean)
case class QuestionHull(question: String, qtype: String, atype: String, answer: List[String])

// Store basic functions that can be used to access the DB
abstract trait CorporaFacade {
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val db: Database

  //! Checks if the given corpora already exists
  def exists(name: String): Future[Boolean] = {
    val query = Tables.corpora.filter(_.name === name).exists
    db.run(query.result)
  }

  //! Creates a new question corpus
  def create(corpus: CorpusInfo): Future[Unit] = {
    val action = Tables.corpora += Tables.Corpus(corpus.name, corpus.description)
    val promise = Promise[Unit]()
    db.run(action).onComplete {
      case Success(c) if c > 0 => promise.success(Unit)
      case Success(c) => promise.failure(new Exception("No rows affected"))
      case Failure(t) => promise.failure(t)
    }
    promise.future
  }

  //! Defines the given corpus as deperacted (delete not possible)
  def deprecate(name: String): Future[Unit] = {
    val up = for { q <- Tables.corpora if q.name === name } yield q.deprecated
    db.run(up.update(true)).map(x => Unit)
  }

  //! Adds a new question to a corpus
  def addQuestion(corpus: String, question: QuestionHull): Future[Int] = {
    val idAction =
      (Tables.questions returning Tables.questions.map(_.id)) += Tables.Question(None, corpus, question.question, question.qtype, question.atype)
    val promise = Promise[Int]()
    db.run(idAction).onComplete {
      case Success(id) =>
        val action = Tables.answers ++= question.answer.map(a => Tables.Answer(None, id, corpus, a)).toIterable
        db.run(action).onComplete {
          case Success(x) => promise.success(id)
          case Failure(t) => promise.failure(t)
        }
      case Failure(e) => promise.failure(e)
    }
    promise.future
  }

  //! Adds a batch of questions to a sepcific corpus
  def addQuestionBatch(corpus: String, questions: List[QuestionHull]): Future[Unit] = {
    val action = DBIO.seq(
      Tables.questions.map(q => (q.corpus, q.question, q.qtype, q.atype)) ++= questions.map(q => (corpus, q.question, q.qtype, q.atype)).toSeq
    )
    db.run(action)
  }

  //! Deprecate a question (delete not possible)
  def deprecateQuestion(corpus: String, id: Int): Future[Unit] = {
    val up = for { q <- Tables.questions if q.corpus === corpus && q.id === id } yield q.deprecated
    db.run(up.update(true)).map(x => Unit)
  }

  //! Returns the questions of a specific corpora
  def get(name: String): Future[CorporaInfo] = {
    val queryCorpus = for { c <- Tables.corpora if c.name === name } yield (c.name, c.description, c.deprecated)
    val queryQuestions = for { q <- Tables.questions if q.corpus === name } yield (q.id, q.question, q.qtype, q.atype, q.deprecated)
    // query database
    val corpus = db.run(queryCorpus.take(1).result).map(c => CorporaHull(c.head._1, c.head._2, c.head._3))

    def subquery(ls: List[(Int, String, String, String, Boolean)]): List[Future[QuestionInfo]] = ls.map( question => {
      val queryAnswer = for { a <- Tables.answers if a.question === question._1 && a.corpus === name } yield a.answer
      db.run(queryAnswer.result).map(answer => QuestionInfo(question._1, question._2, question._3, question._4, answer.toList, question._5))
    })
    val questions = db.run(queryQuestions.result).flatMap( seq => Future sequence subquery(seq.toList) )

    // zip futures
    corpus.zip(questions).map(data => CorporaInfo(data._1.name, data._1.description, data._2, data._1.deprecated))
  }

  //! Returns a list of all available corpora
  def list: Future[List[CorporaHull]] = {
    val query = for { c <- Tables.corpora } yield (c.name, c.description, c.deprecated)
    db.run(query.result).map(seq => seq.toList.map(t => CorporaHull(t._1, t._2, t._3)))
  }
}

//! Maintains connections to the database
class Corpora extends CorporaFacade {
  // create the implicit database to work with
  override implicit val db = Database.forConfig("elementary.util.data.questiondb")

  // command to close the connection
  def close = db.close
}

object Corpora {
  def create = new Corpora()
}
