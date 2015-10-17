package elementary.util.machinelearning

import epic.preprocess.{MLSentenceSegmenter, TreebankTokenizer}

class MLTools {
  lazy val sentenceSplitter = MLSentenceSegmenter.bundled().get
  lazy val tokenizer = new TreebankTokenizer()
}
