package elementary.pipeline.analysis

import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import elementary.util.machinelearning.structures._

object QuestionType {
  // Pos tags determining questions
  val questiontags = List("WP", "WRB")

  // Computes the question type
  def compute(q: QueryRef, b: List[String], pos: List[PosLabel], ner: Map[String, String]): QuestionType = {
    //extract Wh-determiners and wh-adverbs
    val qw: List[String] = pos.filter(t => t match {
      case PosLabel(w, "WP") => true
      case PosLabel(w, "WRB") => true
      case _ => false
    }).map(q => q match {
      case PosLabel(w, _) => w.toLowerCase
    })

    //naiive match of first determiner
    qw match {
      case x :: _ if(x == "why") => WhyQT
      case x :: _ if(x == "how") => HowQT
      case x :: _ if(x == "who") => WhoQT
      case x :: _ if(x == "where") => WhereQT
      case x :: _ if(x == "what") => WhatQT
      case x :: _ if(x == "when") => WhenQT
      case x :: _ if(x == "which") => WhichQT
      case _ => OtherQT
    }
  }
}
