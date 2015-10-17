package elementary.tools.corpusanalyzer

import akka.stream.Materializer
import epic.models.{NerSelector, PosTagSelector}
import epic.preprocess.{MLSentenceSegmenter, TreebankTokenizer}
import epic.sequences.SemiCRF

import elementary.util.data.DBpediaIndex

object AnalysisFct {
  // holds val that are used throughout the system
  val nerBaseModel = NerSelector.loadNer("en").get
  val posBaseModel = PosTagSelector.loadTagger("en").get
  val sentenceSplitter = MLSentenceSegmenter.bundled().get
  val tokenizer = new TreebankTokenizer()
  val nerDBPModel = {
    // TODO
    //val data: IndexedSeq[Segmentation[Label, String]] = ???
    //SemiCRF.buildSimple(data, )
  }

  // convert the json to the elastic document
  def convertEs(json: String)(implicit mat: Materializer): EsDocument = {
    import spray.json._
    import DefaultJsonProtocol._

    val jsonAst = json.parseJson.asJsObject()
    val topic = jsonAst.getFields("module").headOption match {
      case Some(JsString(value)) => value
      case _ => ""
    }
    val lecture = jsonAst.getFields("acaddept").headOption match {
      case Some(JsString(value)) => value
      case _ => ""
    }
    val text = jsonAst.getFields("text").headOption match {
      case Some(JsString(value)) => value
      case _ => ""
    }

    EsDocument(text, lecture, topic)
  }

  def tokenizeText(text: String): IndexedSeq[String] = epic.preprocess.tokenize(text)

  // use the standard breeze model to search for named entities
  def searchBreezeNER(tokens: IndexedSeq[String]): Map[(String, String), Int] = searchBreezeCRF(tokens, nerBaseModel)

  // use the dbpedia crf model to search for named entities
  def searchDBpediaNER(tokens: IndexedSeq[String]): Map[(String, String), Int] = {
    //searchBreezeCRF(tokens, nerDBPModel)
    Map()
  }

  // analyzes the given text on base of the given SemiCRF model
  def searchBreezeCRF(tokens: IndexedSeq[String], model: SemiCRF[Any, String]): Map[(String, String), Int] = {
    try {
      // tokenize the text and detect entities
      lazy val res = model.bestSequence(tokens)
      lazy val segs = res.segments.toStream
      lazy val words = res.words.toStream
      // convert the results into a map
      lazy val res2 = segs.map(item => (item._1.toString, words.slice(item._2.begin, item._2.end).fold("")(_ + " " + _).toLowerCase))
      res2.map{ t => (t, res2.count(_ == t)) }.toMap
    }
    catch {
      case e: Throwable =>
        // TODO remove
        println(e)
        Map()
    }
  }

  // retrieves POS tags from the sentences
  def searchBreezePOS(tokens: IndexedSeq[String]): Map[(String, String), Int] = {
    try {
      // tokenize the text and detect entities
      val res = posBaseModel.bestSequence(tokens)
      lazy val tags = res.tags.toStream
      lazy val words = res.words.toStream
      // convert the results into a map
      lazy val res2 = tags.zip(words).map(t => (t._1.label, t._2))
      res2.groupBy(identity).mapValues(x=>x.size)
    }
    catch {
      case e: Throwable =>
        // TODO remove
        println(s"Exception: $e / Text-Length: ${tokens.size}")
        Map()
    }
  }

  // calculates the length (in words) of the sentences
  def calcSentenceLength(text: String): Map[Int, Int] = {
    lazy val sent = sentenceSplitter(text).toStream
    lazy val res = sent.map(tokenizer(_).size)
    res.groupBy(identity).mapValues(x=>x.size)
  }
}
