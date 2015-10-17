package elementary.util.machinelearning

import elementary.glue.PythonInterop
import com.typesafe.config.ConfigFactory
import scala.util.{Try, Success, Failure}

// Holds the functions for Doc2Vec
class Doc2VecPy(python: PythonInterop) extends Word2Vec {
  import scala.concurrent.ExecutionContext.Implicits.global

  // check if the current model contains this word
  def contains(word: String): Boolean = {
    // NOTE: cannot be answered by gensim
    false
  }

  // returns the vector for the given word (if contained in model)
  def vector(word: String): Option[Array[Double]] = {
    // call the python function and check for the results (if success cast it)
    python.callFunction("similarVector", word) match {
      case Right(data) => Some(data.asInstanceOf[jep.NDArray[Array[Float]]].getData().map(_.toDouble))
      case Left(e)     => None
    }
  }

  // calculate the centroid of the given words (None if at least one word not in model)
  def centroid(words: List[String]): Option[Array[Double]] = {
    paragraph(words.foldLeft("")((o,n) => o + " " + n))
  }

  // calculates the centroid of given words (assumes Zero-Vector for missing)
  def centroidTolerant(words: List[String]): Array[Double] = {
    centroid(words) match {
      case Some(vec) => vec
      case None      => Array.fill(20)(0.0)
    }
  }

  // calculate the paragraph vector
  def paragraph(text: String): Option[Array[Double]] = {
    python.callFunction("similarVectorDoc", text) match {
      case Right(data) => Some(data.asInstanceOf[jep.NDArray[Array[Float]]].getData().map(_.toDouble))
      case Left(e)     => None
    }
  }

  // calculate the paragraph vector and ignores missing words
  def paragraphTolerant(text: String): Array[Double] = {
    paragraph(text) match {
      case Some(vec) => vec
      case None      => Array.fill(20)(0.0)
    }
  }

  // calculate cosine similarity between two words
  def cosine(word1: String, word2: String): Option[Double] = {
    python.callFunction("cosineWords", word1, word2) match {
      case Right(data) => Some(data.asInstanceOf[Double])
      case Left(e)     => None
    }
  }

  // calculate the nearest match for the given vector
  def nearestNeighbors(vector: Array[Double], inSet: Option[Set[String]] = None,
    outSet: Set[String] = Set[String](), N: Int = 40): List[(String, Double)] = {

    List()
  }

  // calculate the most probable items according to their nearest neighbor performance
  def nearestNeighborItems(vector: Array[Double], items: List[(String, Array[Double])], N: Int = 40): List[(String, Double)] = {
    VectorMath.nearestNeighbors(vector, items, N)
  }

  // calculates the analogy for word 1 is to word 2 like word 3 to X
  def analogy(word1: String, word2: String, word3: String, N: Int = 40): List[(String, Double)] = {
    nearWords(List(word1, word3), List(word2), N)
  }

  // calculates a list of words near the one provided
  def nearWords(pos: List[String], neg: List[String], N: Int = 40): List[(String, Double)] = {
    import scala.collection.JavaConversions._
    python.callFunction("mostSimilar", pos.toArray, neg.toArray) match {
      case Right(data) =>
        // cast the data to an array list with a list inside, cast the objects in the list to the actual values
        data.asInstanceOf[java.util.ArrayList[java.util.List[Object]]].toList.map(arr =>
          Try((arr.get(0).asInstanceOf[String], arr.get(1).asInstanceOf[Double])).toOption
        ).filter(_ != None).map(_.get).take(N)
      case Left(e)     => List()
    }
  }

  // calculates a list of words near the one provided
  def nearWordsText(text: String, N: Int = 40): List[(String, Double)] = {
    import scala.collection.JavaConversions._
    python.callFunction("mostSimilarText", text) match {
      case Right(data) =>
        // cast the data to an array list with a list inside, cast the objects in the list to the actual values
        data.asInstanceOf[java.util.ArrayList[java.util.List[Object]]].toList.map(arr =>
          Try((arr.get(0).asInstanceOf[String], arr.get(1).asInstanceOf[Double])).toOption
        ).filter(_ != None).map(_.get).take(N)
      case Left(e)     => List()
    }
  }

  // rank the given words according to their relavance regarding the given word
  def rank(word: String, set: Set[String]): List[(String, Double)] = {
    List()
  }

  // calculate the distance
  def distance(input: List[String], N: Int = 40): List[(String, Double)] = {
    List()
  }
}

object Doc2VecPy {
  // creates the python interop and creates a model
  def create(model: String): Either[Throwable, Doc2VecPy] = {
    // load the python interop interface
    val config = ConfigFactory.load()
    val pyOpt = PythonInterop.create
    // check if python created correctly
    pyOpt match {
      case Right(py) =>
        // load the exec script
        py.loadScript(config.getString("elementary.machinelearning.word2vec.doc2vec_python")) match {
          case Right(true)  => {
            // load the actual model
            py.callFunction("load", model) match {
              case Right(obj)  =>
                if(obj.asInstanceOf[Boolean]) Right( new Doc2VecPy(py) )
                else                          Left(  new Exception("Failed to load model (unspecified)") )
              case Left(e)     =>             Left(  e )
            }
          }
          case Right(false) => Left(new Exception("Failed to load script (unspecified)"))
          case Left(e)      => Left(e)
        }
      case Left(e) => Left(e)
    }
  }
}
