/*package elementary.util.machinelearning

import com.typesafe.config.ConfigFactory
import org.deeplearning4j.models.word2vec.{Word2Vec => Word2Vec4j, _}
import scala.collection.JavaConversions._
import scala.util.{Try}

class Word2VecDeep4j(vec: Word2Vec4j) extends Word2Vec {
  // holds all words (performance reasons)
  val words = vec.vocab().vocabWords().toList.map(word => (word.getWord(), vec.getWordVector(word.getWord()).map(_.toFloat)))

  def contains(word: String): Boolean = {
    vec.hasWord(word)
  }

  def vector(word: String): Option[Array[Float]] = {
    Try(vec.getWordVector(word)).toOption.map(_.map(_.toFloat))
  }

  def centroid(words: List[String]): Option[Array[Float]] = {
    val vecsOpt = words.map(vector)
    if (vecsOpt.contains(None) || vecsOpt.size == 0) None
    else {
      val vecs = vecsOpt.map(_.get)
      val start = Array.fill(vecs.head.size)(0.0f)
      Some(vecs.foldLeft(start)((old,vec) => old.zip(vec).map(tpl => tpl._1 + tpl._2.toFloat)).map(_ / vecs.size))
    }
  }

  def centroidTolerant(words: List[String]): Array[Float] = {
    val vecs = words.map(vector).filter(_ != None).map(_.get)
    val start = Array.fill(vecs.head.size)(0.0f)
    vecs.foldLeft(start)((old,vec) => old.zip(vec).map(tpl => tpl._1 + tpl._2.toFloat)).map(_ / vecs.size)
  }

  def cosine(word1: String, word2: String): Option[Double] = {
    Try( vec.similarity(word1, word2) ).toOption
  }

  def nearestNeighbors(vector: Array[Float], inSet: Option[Set[String]] = None,
    outSet: Set[String] = Set[String](), N: Int = 40): List[(String, Float)] = {
    // calciulate the list of all used vectors
    val items =
      if (inSet.isDefined) words.filter(tpl => inSet.get.contains(tpl._1) && !outSet.contains(tpl._1))
      else if (outSet.size == 0) words
      else words.filter(tpl => !outSet.contains(tpl._1))
    VectorMath.nearestNeighbors(vector, items, N)
  }

  def analogy(word1: String, word2: String, word3: String, N: Int = 40): List[(String, Float)] = {
    vec.wordsNearest(List(word1, word3), List(word2), N).map(str => (str, 1.0f)).toList;
  }

  def rank(word: String, set: Set[String]): List[(String, Float)] = {
    set.toList.map(w => ( w, Try(vec.similarity(word, w)).toOption.getOrElse(0.0).toFloat )).sortWith((a,b) => a._2 > b._2)
  }

  def distance(input: List[String], N: Int = 40): List[(String, Float)] = {
    vec.wordsNearest(input, List(), N).map(w => (w, 1.0f)).toList
  }
}

object Word2VecDeep4j {
  // loads the model based on the config file
  def load(): Either[Throwable, Word2VecDeep4j] = {
    val config = ConfigFactory.load()
    load(config.getString("elementary.parameter.general.dsm-folder") + config.getString("elementary.parameter.general.ds-model"),
      config.getInt("elementary.parameter.general.ds-limit") match { case 0 => Int.MaxValue case x: Int => x},
      config.getBoolean("elementary.parameter.general.ds-normalize")
    )
  }

  def load(filename: String, limit: Int = Int.MaxValue, normalize: Boolean = true): Either[Throwable, Word2VecDeep4j] = {
    // TODO load the word2vec
    //val w2v = Word2Vec4j

    //Right(new Word2VecDeep4j(w2v))
    Left(???)
  }
}*/
