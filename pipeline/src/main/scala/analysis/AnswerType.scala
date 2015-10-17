package elementary.pipeline.analysis

import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import elementary.util.machinelearning.structures._

object AnswerType {
  def compute(q: QueryRef, b: List[String], pos: List[PosLabel], ner: Map[String, String]): AnswerType = {
    //naiive matching of last entity in the sentence as the answer type
    val at = ner.map {
      case (w, "LOC") => LocationFAT
      case (w, "MISC") => UnknownNFAT
      case (w, "PER") => PersonFAT
      case (w, "ORG") => EntityNFAT(w)
      case (w, "DATE") => DateFAT
      case (w, "TIME") => DateFAT
      case (w, "LANG") => LocationFAT
      case _ => UnknownNFAT
    }

    at match {
      case List() => UnknownNFAT
      case l => l.last
    }
  }

}
