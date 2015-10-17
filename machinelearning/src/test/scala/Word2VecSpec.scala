import org.scalatest.{WordSpecLike, Matchers, FlatSpec}
import elementary.util.machinelearning.Word2Vec

class Word2VecSpec extends FlatSpec with Matchers {
  // model shouldBe loaded only once
  lazy val model = {
    val time = System.currentTimeMillis
    val model = Word2Vec.load("C:/Programming/university/learning/scala/machine-learning/word2vec/word2vec-scala-master/vectors.bin")
    val diff = System.currentTimeMillis - time
    println(s"Loading Model: \t${diff.toDouble / 1000.0} seconds")
    model match {
      case Right(m) => Some(m)
      case Left(e) => println(s"Error: $e"); None
    }
  }

  "Word2Vec" should "should be loaded" in {
    model.get.wordsCount shouldBe (71290)
    model.get.vectorSize shouldBe (200)
  }

  it should "contain certain words" in {
    model.get.contains("queen") shouldBe (true)
    model.get.contains("woman") shouldBe (true)
    model.get.contains("iron man") shouldBe (false)
  }

  it should "allow sum operations" in {
    val time = System.currentTimeMillis
    val vec = model.get.sumVector(List("queen", "woman"))
    val diff = System.currentTimeMillis - time
    println(s"Sum Operation: \t${diff.toDouble / 1000.0} seconds")
    vec shouldBe ('right)
  }

  it should "allow analogies" in {
    val time = System.currentTimeMillis
    val vec = model.get.analogy("son", "boy", "girl")
    val diff = System.currentTimeMillis - time
    println(s"Analogy Operation: \t${diff.toDouble / 1000.0} seconds")
    vec.maxBy(_._2)._1 should be ("daughter")
  }
}
