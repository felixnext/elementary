package elementary.util.machinelearning

import collection.JavaConversions._
import epic.models.{NerSelector, ParserSelector}
import epic.preprocess.{MLSentenceSegmenter, TreebankTokenizer}
import epic.util.SafeLogging
import scalaz.Scalaz._

//! Defines basic extraction for language features
object LanguageFeatures {
  implicit class LanguageFeaturer(base: MLTools) {
    // some elements that are used by most functions and are created if needed
    lazy val stopRegex = """[#\?\.\,\&\!\*]+""".r

    //! Calculates the bag of words for the given text (lowercase)
    def bagOfWords(text: String): Set[String] = {
      base.sentenceSplitter(text).flatMap(base.tokenizer).toSet
    }

    //! Counts the words in the given text
    def wordCount(text: String): Map[String, Int] = {
      base.sentenceSplitter(text).flatMap(base.tokenizer).groupBy(identity).mapValues(_.size)
    }

    //! Calculates the frequency of words
    def wordFrequency(text: String): Map[String, Double] = {
      val ls = wordCount(text)
      val count = ls.foldLeft(0)(_ + _._2).toDouble
      ls.mapValues(x => (x.toDouble / count))
    }

    //! Calculates all word co-occurences sentence wise
    def wordCoocSentence(text: String): Map[(String, String), Int] = {
      // loop that iterates through all sentences
      @annotation.tailrec
      def loop(ls: List[List[String]], result: Map[(String, String), Int] = Map()): Map[(String, String), Int] = {
        ls match {
          case sentence :: tail =>
            val pairs = (sentence |@| sentence){(_,_)}.filterNot(x => x._1 == x._2).map(x => if(x._1 > x._2) (x._2, x._1) else x)
            loop(tail, pairs.groupBy(identity).mapValues(_.size / 2) |+| result)
          case Nil => result
        }
      }

      // call the loop with a list of sentence words
      loop( base.sentenceSplitter(text).toList.map(base.tokenizer(_).toList.filterNot(x => stopRegex.pattern.matcher(x).matches)) )
    }

    //! Calculate all word co-occurences word wise
    def wordCoocWord(text: String): Map[(String, String), Int] = {
      // loop that iterates through all sentences
      @annotation.tailrec
      def loop(ls: List[List[String]], result: Map[(String, String), Int] = Map()): Map[(String, String), Int] = {
        ls match {
          case sentence :: tail =>
            @annotation.tailrec
            def innerLoop(words: List[String], result: List[(String, String)] = List()): List[(String, String)] = {
              words match {
                case word1 :: word2 :: tail_in if (word1 != word2) =>
                  innerLoop(word2 :: tail_in, result :+ ( if(word1>word2) (word2,word1) else (word1,word2) ))
                case _ => result
              }
            }
            // recursive call / count the tuples from the inner loop and merge with current results
            loop(tail, innerLoop(sentence).groupBy(identity).mapValues(_.size) |+| result)
          case Nil => result
        }
      }

      // call the loop with a list of sentence words
      loop( base.sentenceSplitter(text).toList.map(base.tokenizer(_).toList.filterNot(x => stopRegex.pattern.matcher(x).matches)) )
    }
  }
}
