import org.scalatest.{WordSpecLike, Matchers, FlatSpec}
import elementary.util.machinelearning.MLTools
import elementary.util.machinelearning.TextExtraction._

class TextExtractionSpec extends FlatSpec with Matchers {
  val features = new MLTools()
  "TextExtraction" should "find candidates in a text" in {
    /*val bag = features.bagOfWords("Hello World, who are you? Hello World. WHO are you!".toLowerCase)
    bag should be ( Set("hello", "world", "who", "are", "you", ",", "?", "!", ".") )*/
  }
}
