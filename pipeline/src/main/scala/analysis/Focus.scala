package elementary.pipeline.analysis

import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import elementary.util.machinelearning.structures._

object Focus {
  // Detects the focus of the question
  def compute(q: QueryRef, b: List[String], pos: List[PosLabel], ner: Map[String, String]): List[String] = {
    //extract nouns as focus
    pos.filter(t => t match {
      //case NN(w) => true
      //case NNS(w) => true
      //case NNP(w) => true
      //case NNPS(w) => true
      case PosLabel(w, "NP") => true
      case _ => false
    }).map(q => q match {
      case PosLabel(w, _) => w
    })
  }
}
