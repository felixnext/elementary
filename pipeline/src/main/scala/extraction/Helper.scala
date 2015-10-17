package elementary.pipeline.extraction

import elementary.util.machinelearning.VectorMath
import elementary.util.data.TranscriptVector
import com.typesafe.config.ConfigFactory
import scala.util.Try

object Helper {
  // load the limit for the vector search
  val vecLimit = {
    val config = ConfigFactory.load()
    Try(config.getInt("")).toOption.getOrElse(0)
  }

  def nearestSnip(vector: Array[Double], vecs: List[TranscriptVector]): List[(String, Double)] = {
    nearest(vector, vecs)(vec => (vec.id, vec.vector))
  }

  def nearestTrans(vector: Array[Double], vecs: List[TranscriptVector]): List[(String, Double)] = {
    nearest(vector, vecs)(vec => (vec._id, vec.vector))
  }

  // searches the nearest neighbors for the given vector
  def nearest(vector: Array[Double], vecs: List[TranscriptVector])(f: TranscriptVector => (String, Array[Double])): List[(String, Double)] = {
    VectorMath.nearestNeighbors(vector, vecs.map(f), vecLimit)
  }
}
