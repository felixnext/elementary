// Original Code: Copyright 2013 trananh - Licensed under the Apache License, Version 2.0 (the "License");
package elementary.util.machinelearning

import java.io._
import com.typesafe.config.ConfigFactory
//import scala.Array
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}
import elementary.util.machinelearning.structures.VecBinaryReader

// Interface to performe operations on the Word2Vec models
class Word2VecLocal(vocab: Map[String, Array[Double]], numWords: Int, vecSize: Int) extends Word2Vec {
  import scala.concurrent.ExecutionContext.Implicits.global

  def wordsCount: Int = numWords
  def vectorSize: Int = vecSize

  // Check if the word is present in the vocab map.
  def contains(word: String): Boolean = vocab.get(word).isDefined
  def vector(word: String): Option[Array[Double]] = vocab.get(word)

  def centroid(words: List[String]): Option[Array[Double]] = sumVector(words).map(_.map(_ / words.size))

  def centroidTolerant(words: List[String]): Array[Double] = sumVectorTolerant(words).map(_ / words.size)

  // calculate the paragraph vector
  def paragraph(text: String): Option[Array[Double]] = None

  // calculate the paragraph vector and ignores missing words
  def paragraphTolerant(text: String): Array[Double] = Array.fill(vecSize)(0.0)

  // Find the vector representation for the given list of word(s) by aggregating (summing) the vector for each word.
  def sumVector(input: List[String]): Option[Array[Double]] = {
    val vecsOpt = input.map(vector)
    if (vecsOpt.contains(None) || vecsOpt.size == 0) None
    else {
      val vecs = vecsOpt.map(_.get)
      val start = Array.fill(vecSize)(0.0)
      Some(vecs.foldLeft(start)((old,vec) => old.zip(vec).map(tpl => tpl._1 + tpl._2.toDouble)).map(_ / vecs.size))
    }
  }

  // Find the vector representation for the given list of word(s) by aggregating (summing) the vector for each word.
  // NOTE: ignores unkown words
  def sumVectorTolerant(input: List[String]): Array[Double] = {
    val vecs = input.map(vector).filter(_ != None).map(_.get)
    val start = Array.fill(vecSize)(0.0)
    vecs.foldLeft(start)((old,vec) => old.zip(vec).map(tpl => tpl._1 + tpl._2))
  }

  // Compute the cosine similarity score between the vector representations of the words.
  def cosine(word1: String, word2: String): Option[Double] = {
    (vocab.get(word1), vocab.get(word2)) match {
      case (None, _) => None
      case (_, None) => None
      case (Some(w1), Some(w2)) => VectorMath.cosine(w1, w2)
    }
  }

  // Compute the Euclidean distance between the vector representations of the words.
  def euclidean(word1: String, word2: String): Option[Double] = {
    (vocab.get(word1), vocab.get(word2)) match {
      case (None, _) => None
      case (_, None) => None
      case (Some(w1), Some(w2)) => VectorMath.euclidean(w1, w2)
    }
  }

  // Find N closest terms in the vocab to the given vector, using words from inSet and excluding form outSet
  def nearestNeighbors(vector: Array[Double], inSet: Option[Set[String]] = None,
    outSet: Set[String] = Set[String](), N: Int): List[(String, Double)] = {
    // filter the elements
    val items =
      if (inSet.isDefined) vocab.filterKeys(k => inSet.get.contains(k) && !outSet.contains(k))
      else if (outSet.size == 0) vocab
      else vocab.filterKeys(k => !outSet.contains(k))
    VectorMath.nearestNeighbors(vector, items.toList, N)
  }

  def nearestNeighborItems(vector: Array[Double], items: List[(String, Array[Double])], N: Int = 40): List[(String, Double)] = {
    List()
  }

  // Find the N closest terms in the vocab to the input word(s).
  def distance(input: List[String], N: Int = 40): List[(String, Double)] = {
    // Check for edge cases
    if (input.size == 0) List()
    else {
      sumVector(input) match {
        case Some(vector) => nearestNeighbors(VectorMath.normalize(vector), outSet = input.toSet, N = N)
        case None => List()
      }
    }
  }
  // Find a vector approximation of the missing word = vec([word2]) - vec([word1]) + vec([word3]) and return the closest vector
  def analogy(word1: String, word2: String, word3: String, N: Int): List[(String, Double)] = {
    if (!contains(word1) || !contains(word2) || !contains(word3)) List()
    else {
      val (v1, v2, v3) = (vocab.get(word1).get, vocab.get(word2).get, vocab.get(word3).get)
      val vec = v1.zip(v2).zip(v3).par.map(tpl => tpl._1._1 - tpl._1._2 + tpl._2).toArray
      nearestNeighbors(VectorMath.normalize(vec), outSet = Set(word1, word2, word3), N = N)
    }
  }

  // calculates a list of words near the one provided
  def nearWords(pos: List[String], neg: List[String], N: Int = 40): List[(String, Double)] = List()
  // calculates a list of words near the one provided
  def nearWordsText(text: String, N: Int = 40): List[(String, Double)] = List()

  // Rank the list of words on their distance to the base word
  def rank(word: String, set: Set[String]): List[(String, Double)] = {
    if (set.size == 0) List()
    else {
      val setOpt = set.map(w => vocab.get(w))
      if (setOpt.contains(None) || !contains(word)) List()
      else nearestNeighbors(vocab.get(word).get, inSet = Option(set), N = set.size)
    }
  }
  // Pretty print function for the word list
  def prettyPrint(words: List[(String, Double)]) = {
    println("\n%50s".format("Word") + (" " * 7) + "Cosine distance\n" + ("-" * 72))
    println(words.map(s => "%50s".format(s._1) + (" " * 7) + "%15f".format(s._2)).mkString("\n"))
  }
}

object Word2VecLocal {
  // loads the model based on the config file
  def load(): Either[Throwable, Word2VecLocal] = {
    val config = ConfigFactory.load()
    load(config.getString("elementary.parameter.general.dsm-folder") + config.getString("elementary.parameter.general.ds-model"),
      config.getInt("elementary.parameter.general.ds-limit") match { case 0 => Int.MaxValue case x: Int => x},
      config.getBoolean("elementary.parameter.general.ds-normalize")
    )
  }
  // loads the binary vocab model (limit = max vocab size to load)
  def load(filename: String, limit: Int = Int.MaxValue, normalize: Boolean = true): Either[Throwable, Word2VecLocal] = {
    // iterate through the reader and load all words
    @annotation.tailrec
    def loop(i: Int, bound: Int, vecSize: Int, reader: VecBinaryReader,
      map: Map[String, Array[Double]] = Map()): Either[Throwable, Map[String, Array[Double]]] = {
      if(i < bound) {
        def vectorLoop() = Try(List.fill(vecSize)(reader.readFloat() match { case Left(e) => throw e case Right(f) => f.toDouble }).toArray)
        (reader.readToken(), vectorLoop()) match {
          case (Left(e), _) => Left(e)
          case (_, Failure(e)) => Left(e)
          case (Right(word), Success(vector)) => {
            val normFactor = if (normalize) VectorMath.magnitude(vector) else 1f
            reader.read()
            loop(i+1, bound, vecSize, reader, map + (word -> vector.map(_ / normFactor)))
          }
        }
      }
      else Right(map)
    }
    // catch all possible errors in monads
    val file = new File(filename)
    if (!file.exists()) Left(new FileNotFoundException(s"the file '$filename' was not found!"))
    else {
      val reader = new VecBinaryReader(file)
      val numWordsOpt = reader.readToken()
      val vecSizeOpt = reader.readToken()
      (numWordsOpt, vecSizeOpt) match {
        case (Left(e), _) => Left(e)
        case (_, Left(e)) => Left(e)
        case (Right(numWords), Right(vecSize)) =>
          loop(0, math.min(Integer.parseInt(numWords), limit), Integer.parseInt(vecSize), reader) match {
            case Left(e) => Left(e)
            case Right(vocab) => {
              reader.close()
              Right(new Word2VecLocal(vocab, Integer.parseInt(numWords), Integer.parseInt(vecSize)))
            }
          }
      }
    }
  }
}
