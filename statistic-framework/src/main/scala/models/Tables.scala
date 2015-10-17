package elementary.statistic.models

import slick.driver.MySQLDriver.api._
import slick.lifted.{ProvenShape, ForeignKeyQuery}

object Tables {
  // The corpus table that stores all available corpora
  case class Corpus(name: String, description: String, deprecated: Boolean = false)
  class Corpora(tag: Tag)
    extends Table[Corpus](tag, "Corpora") {

    // This is the primary key column:
    def name: Rep[String] = column[String]("name", O.PrimaryKey)
    def description: Rep[String] = column[String]("description")
    def deprecated: Rep[Boolean] = column[Boolean]("deprecated", O.Default(false))

    // Every table needs a * projection with the same type as the table's type parameter
    def * = (name, description, deprecated) <> (Corpus.tupled, Corpus.unapply)
  }
  val corpora = TableQuery[Corpora]

  // The question table that stores all available questions
  case class Question(id: Option[Int], corpus: String, question: String, qtype: String, atype: String, deprecated: Boolean = false)
  class Questions(tag: Tag)
    extends Table[Question](tag, "Questions") {

    def id: Rep[Int] = column[Int]("id", O.AutoInc)
    def corpus: Rep[String] = column[String]("corpus")
    def question: Rep[String] = column[String]("question")
    def qtype: Rep[String] = column[String]("qtype")
    def atype: Rep[String] = column[String]("atype")
    def deprecated: Rep[Boolean] = column[Boolean]("deprecated", O.Default(false))

    def * = (id.?, corpus, question, qtype, atype, deprecated) <> (Question.tupled, Question.unapply)

    // A reified foreign key relation that can be navigated to create a join
    def pk = primaryKey("pk_questions", (id, corpus))
    def corpora: ForeignKeyQuery[Corpora, Corpus] =
      foreignKey("CORPORA_FK", corpus, TableQuery[Corpora])(_.name)
  }
  val questions = TableQuery[Questions]

  // The answer table stores all answers related to a specific question
  case class Answer(id: Option[Int], question: Int, corpus: String, answer: String)
  class Answers(tag: Tag)
    extends Table[Answer](tag, "Answers") {

    def id: Rep[Int] = column[Int]("id", O.AutoInc)
    def question: Rep[Int] = column[Int]("question")
    def corpus: Rep[String] = column[String]("corpus")
    def answer: Rep[String] = column[String]("answer")

    def * = (id.?, question, corpus, answer) <> (Answer.tupled, Answer.unapply)

    // A reified foreign key relation that can be navigated to create a join
    def pk = primaryKey("pk_answers", (id, question, corpus))
    def questions: ForeignKeyQuery[Questions, Question] =
      foreignKey("QUESTIONS_FK", (question, corpus), TableQuery[Questions])(q => (q.id, q.corpus))
  }
  val answers = TableQuery[Answers]

  // defines implicit conversion for sql statements
  /*trait Transfer { this: PlainSQL.type =>
    // Result set getters
    implicit val getCorporaResult = GetResult(r => CorporaData(r.nextString, r.nextString, r.nextBoolean))
    // TODO fix that
    implicit val getQuestionResult = GetResult(r => QuestionData(r.nextInt, r.nextString, r.nextString, r.nextString, Nil, r.nextBoolean))
  }*/
}
