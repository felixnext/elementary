package elementary.pipeline.analysis

import epic.models.{NerSelector, PosTagSelector, ParserSelector}
import epic.preprocess.{MLSentenceSegmenter, TreebankTokenizer}
import epic.sequences.SemiCRF

import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import elementary.util.machinelearning.structures._

object Epic {
  lazy val nerBaseModel = NerSelector.loadNer("en").get
  lazy val posBaseModel = PosTagSelector.loadTagger("en").get
  lazy val parser = ParserSelector.loadParser("en").get

  // Computes a bag of words
  def bagOfWords(q: QueryRef): List[String] = {
     epic.preprocess.tokenize(q.query).toList
  }

  // Part of speech tagging of the question
  def partOfSpeech(t: List[String]): List[PosLabel] = {
    import elementary.util.machinelearning.structures.Conversions.{EpicPosExt, EpicParserExt}
    //posBaseModel.bestSequence(t.toIndexedSeq).toPosLabels
    parser(t.toIndexedSeq).toPosLabels(t)
  }

  // Recognizes the named entities in the question
  def namedEntityRecognition(t: List[String]): Map[String, String] = {

    // analyzes the given text on base of the given SemiCRF model
    def searchBreezeCRF(tokens: IndexedSeq[String], model: SemiCRF[Any, String]): Map[String, String] = {
      // tokenize the text and detect entities
      lazy val res = model.bestSequence(tokens)
      lazy val segs = res.segments.toStream
      lazy val words = res.words.toStream

      // convert the results into a map
      segs.map(item => (words.slice(item._2.begin, item._2.end).fold("")(_ + " " + _).toLowerCase, item._1.toString)).toMap
    }

    searchBreezeCRF(t.toIndexedSeq, nerBaseModel)
  }
}
