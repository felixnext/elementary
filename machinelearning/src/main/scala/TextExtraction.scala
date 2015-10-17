package elementary.util.machinelearning

import elementary.util.data.DBpediaSpotlight
import elementary.util.data.EntityRef
import com.rockymadden.stringmetric.similarity._
import epic.preprocess.{MLSentenceSegmenter, TreebankTokenizer}
import scala.concurrent.Future

object TextExtraction {
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit class TextExtractor(base: MLTools) {
    // searches all dbpedia entities from the given text
    def detectDBpediaEntities(text: String): Future[List[EntityRef]] = {
      DBpediaSpotlight.search(text).map(ls => ls.map(ent => EntityRef(ent.uri, ent.score, ent.offset,
        ent.surfaceForm.length, ent.surfaceForm, ent.types)))
    }

    // searches for specific topics in the text
    def detectTopics(text: String): Future[List[(String, Double)]] = {
      Future(List())
    }

    // extracts passages from the given text that match the given bow (sentence wise)
    def metricExtraction(text: String, threshold: Double, bow: List[String]): List[(String, Double, Int)] = {
      import LanguageFeatures._

      val bowMap = bow.toSet

      @annotation.tailrec
      def loop(sent: List[String], offset: Int = 0, rank: Double = 1, candidate: String = "",
        res: List[(String, Double, Int)] = List()): List[(String, Double, Int)] = {
        sent match {
          case head::tail =>
            val score = base.bagOfWords(head).filter(word => bowMap.contains(word)).size.toDouble / bow.size.toDouble
            if (score >= threshold) loop(tail, offset, rank * score, candidate + " " + head, res)
            else if (sent.length > 0) loop(tail, offset + candidate.size, res = (candidate, rank, offset) :: res)
            else loop(tail, offset + head.length, res = res)
          case Nil if sent.length > 0 => (candidate, rank, offset) :: res
          case Nil => res
        }
      }

      loop(base.sentenceSplitter(text).toList)
    }
  }
}
