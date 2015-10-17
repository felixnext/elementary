package elementary.util.machinelearning

trait Word2Vec {
  // check if the current model contains this word
  def contains(word: String): Boolean
  // returns the vector for the given word (if contained in model)
  def vector(word: String): Option[Array[Double]]
  // calculate the centroid of the given words (None if at least one word not in model)
  def centroid(words: List[String]): Option[Array[Double]]
  // calculates the centroid of given words (assumes Zero-Vector for missing)
  def centroidTolerant(words: List[String]): Array[Double]
  // calculate the paragraph vector
  def paragraph(text: String): Option[Array[Double]]
  // calculate the paragraph vector and ignores missing words
  def paragraphTolerant(text: String): Array[Double]
  // calculate cosine similarity between two words
  def cosine(word1: String, word2: String): Option[Double]
  // calculate the nearest match for the given vector
  def nearestNeighbors(vector: Array[Double], inSet: Option[Set[String]] = None,
    outSet: Set[String] = Set[String](), N: Int = 40): List[(String, Double)]
  // calculates the nearest items based on a given vector set
  def nearestNeighborItems(vector: Array[Double], items: List[(String, Array[Double])], N: Int = 40): List[(String, Double)]
  // calculates the analogy for word 1 is to word 2 like word 3 to X
  def analogy(word1: String, word2: String, word3: String, N: Int = 40): List[(String, Double)]
  // calculates a list of words near the one provided
  def nearWords(pos: List[String], neg: List[String], N: Int = 40): List[(String, Double)]
  // calculates a list of words near the one provided
  def nearWordsText(text: String, N: Int = 40): List[(String, Double)]
  // rank the given words according to their relavance regarding the given word
  def rank(word: String, set: Set[String]): List[(String, Double)]
  // calculate the distance
  def distance(input: List[String], N: Int = 40): List[(String, Double)]
}
