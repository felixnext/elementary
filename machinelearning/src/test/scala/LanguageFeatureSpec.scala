import org.scalatest.{WordSpecLike, Matchers, FlatSpec}
import elementary.util.machinelearning.MLTools
import elementary.util.machinelearning.LanguageFeatures._

class LanguageFeaturesSpec extends FlatSpec with Matchers {
  val features = new MLTools()
  "LanguageFeatures" should "create Bag of Words" in {
    val bag = features.bagOfWords("Hello World, who are you? Hello World. WHO are you!".toLowerCase)
    bag should be ( Set("hello", "world", "who", "are", "you", ",", "?", "!", ".") )
  }

  it should "count words" in {
    val count = features.wordCount("Hello hello my friend.".toLowerCase)
    count should be ( Map("hello" -> 2, "my" -> 1, "friend" -> 1, "." -> 1) )
  }

  it should "calculate word frequency" in {
    val freq = features.wordFrequency("Hello hello my friend.".toLowerCase)
    freq should be ( Map("hello" -> 2.0/5.0, "my" -> 1.0/5.0, "friend" -> 1.0/5.0, "." -> 1/5.0) )
  }

  it should "calculate co-occurences sentence-wise" in {
    val cooc = features.wordCoocSentence("foo bar baz? baz bar!")
    cooc should be ( Map((("bar", "foo") -> 1), (("baz", "foo") -> 1), (("?", "foo") -> 1), (("bar", "baz") -> 2),
      (("?", "bar") -> 1), (("?", "baz") -> 1), (("!", "baz") -> 1), (("!", "bar") -> 1)  ))
  }

  it should "calculate co-occurences word-wise" in {
    val cooc = features.wordCoocWord("what should we do. we do not know.")
    cooc should be ( Map( (("should", "what") -> 1), (("should", "we") -> 1), (("do", "we") -> 2),
      ((".", "do") -> 1), (("do", "not") -> 1), (("know", "not") -> 1), ((".", "know") -> 1) ))
  }
}
