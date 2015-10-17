package elementary.pipeline.analysis

import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import elementary.util.machinelearning.structures._

object Topic {
  // Detects the topic of the question
  def compute(q: QueryRef, b: List[String], pos: List[PosLabel], ner: Map[String, String]): List[String] = {
    ner.keys.toList
  }
}
