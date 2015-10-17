package elementary.tools.answerscrawler

import epic.models.{NerSelector, PosTagSelector}
import epic.preprocess.{MLSentenceSegmenter, TreebankTokenizer}
import epic.sequences.SemiCRF

object NLPFlow {
  lazy val posBaseModel = PosTagSelector.loadTagger("en").get
  //lazy val proc:Processor = new CoreNLPProcessor(withDiscourse = true)

  //pos tag using the epic tagger
  def posTag(q: Question) : Question = {
    val bow = epic.preprocess.tokenize(q.question)
    val tags = posBaseModel.bestSequence(bow)

    val list = tags.features.zip(tags.label).map(x => (x._1, x._2.treebankString))

    println(list)
    q
  }

  def coreNLP(q: Question) : Question = {
    //val doc = proc.annotate(q.question)
    //println(doc)
    q
  }
}
