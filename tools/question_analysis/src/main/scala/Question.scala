package elementary.tools.answerscrawler

import net.fehmicansaglam.bson.BsonDocument
import scala.language.implicitConversions

case class Question(id: String, url: String, question: String, questiondetail: String,
  category: List[String], bestanswer: String, otheranswers: List[String], taken: Option[Boolean], types: List[String])

object Question {
  implicit def BsonDocumentToQuestion(d: BsonDocument) = {
    val id = d.getAs[String]("id") match {
      case Some(i) => i
      case None => ""
    }

    val url = d.getAs[String]("url") match {
      case Some(u) => u
      case None => ""
    }

    val question = d.getAs[String]("question") match {
      case Some(q) => q
      case None => ""
    }

    val questiondetail = d.getAs[String]("questiondetail") match {
      case Some(d) => d
      case None => ""
    }

    val category = d.getAsList("category") match {
      case Some(l) => l
      case None => List()
    }

    val bestanswer = d.getAs[String]("bestanswer") match {
      case Some(b) => b
      case None => ""
    }

    val otheranswers = d.getAsList("otheranswers") match {
      case Some(o) => o
      case None => List()
    }

    val taken = d.getAs[Boolean]("taken")

    Question(id, url, question, questiondetail, category, bestanswer, otheranswers, taken, List())
  }
}
