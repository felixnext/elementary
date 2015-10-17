package elementary.util.machinelearning.structures

import epic.sequences.TaggedSequence
import epic.trees.{AnnotatedLabel, Tree}

case class PosLabel(words: String, label: String)

object Conversions {
  implicit class EpicPosExt(pos: TaggedSequence[AnnotatedLabel, String]) {
    def toPosLabels: List[PosLabel] = {
      pos.features.zip(pos.label)
        .map(x => (x._1, x._2.treebankString))
        .map(x => PosLabel(x._1, x._2))
        .toList
    }
  }

  implicit class EpicParserExt(pos: Tree[AnnotatedLabel]) {
    def toPosLabels(words: List[String]): List[PosLabel] = {
      @annotation.tailrec
      def loop(trees: List[Tree[AnnotatedLabel]], res: List[PosLabel] = List()): List[PosLabel] = {
        trees match {
          case tree :: tail =>
            loop(
              tail ::: tree.children.toList,
              res :+ PosLabel(words.slice(tree.begin, tree.end).foldLeft("")((o, n) => o + " " + n).substring(1), tree.label.treebankString)
            )
          case Nil => res
        }
      }

      loop(pos.children.toList)
    }
  }
}
