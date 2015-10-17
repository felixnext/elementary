package elementary.tools.answerscrawler

import scala.util.matching.Regex

object AnnotateFlow {

  //annotate questions with wh-words
  def annotateWhWord(q: Question) : Question = {
    val why1 = """^[Ww]hy.*\?""".r
    val how1 = """^[Hh]ow.*\?""".r
    val can1 = """^[Cc]an.*\?""".r
    val why = """.*\s[Ww]hy.*\?""".r
    val how = """.*\s[Hh]ow.*\?""".r
    val can = """.*\s[Cc]an.*\?""".r
    val not = """.*\'t\s.*""".r

    val t = checkPattern(q, why1, "Why1") ++
    checkPattern(q, how1, "How1") ++
    checkPattern(q, can1, "Can1") ++
    checkPattern(q, why, "Why") ++
    checkPattern(q, how, "How") ++
    checkPattern(q, can, "Can") ++
    checkPattern(q, not, "Not")

    println(q.question)
    println(t)

    Question(q.id, q.url, q.question, q.questiondetail, q.category, q.bestanswer, q.otheranswers, q.taken, q.types ++ t)
  }

  //check regex pattern
  def checkPattern(q: Question, p: Regex, typ: String) : List[String] = {
    p.findFirstIn(q.question) match {
      case Some(t) => List(typ)
      case None => List()
    }
  }
}
