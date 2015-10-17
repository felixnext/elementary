package elementary.pipeline.analysis

import akka.actor.ActorRef
import akka.stream.{ActorMaterializer}
import akka.stream.scaladsl.{FlowGraph, Broadcast, Flow, ZipWith, Merge}
import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import elementary.util.machinelearning.structures._
import elementary.pipeline.StatisticsActor

// Defines the pipeline for the question analysis module
object AnalysisPipeline {
  val reporter = StatisticsActor.get

  lazy val flow = Flow() { implicit b =>
    import FlowGraph.Implicits._

    // Flows 0 step
    val signs = Flow[QueryRef].map(ref => {
      def sgn(x: String): Boolean = ref.query.endsWith(x)
      QueryRef(ref.id, if(sgn(".") || sgn("?") || sgn("!")) ref.query else ref.query + "?", ref.sender)
    })

    // Flows 1st Step
    val id = Flow[QueryRef].map(q => q.id)
    val query = Flow[QueryRef].map(q => q.query)
    val sender = Flow[QueryRef].map(q => q.sender)
    val bow = Flow[QueryRef].map(q => Epic.bagOfWords(q))
    val pos = Flow[List[String]].map(q => Epic.partOfSpeech(q))
    val ner = Flow[List[String]].map(q => Epic.namedEntityRecognition(q))
    val reportFlow = Flow[QuestionRef].map(q => {
      val json = s"""{"question-type": "${q.data.qtype}", "answer-type": "${q.data.atype}",
      | "pos":"${q.data.pos.toString}", "foucswords": "${q.data.focus.toString}"}""".stripMargin
      reporter ! ProcessReportMessage(q.id, "analysis", json, System.currentTimeMillis)
      q
    })
    val merge = b.add(Merge[QuestionRef](1))

    // Flows 2nd Step
    val qtype = Flow[(QueryRef, List[String], List[PosLabel], Map[String, String])].map(t => t match {
      case (q, b, pos, ner) => QuestionType.compute(q, b, pos, ner)
    })
    val atype = Flow[(QueryRef, List[String], List[PosLabel], Map[String, String])].map(t => t match {
      case (q, b, pos, ner) => AnswerType.compute(q, b, pos, ner)
    })
    val topic = Flow[(QueryRef, List[String], List[PosLabel], Map[String, String])].map(t => t match {
      case (q, b, pos, ner) => Topic.compute(q, b, pos, ner)
    })
    val focus = Flow[(QueryRef, List[String], List[PosLabel], Map[String, String])].map(t => t match {
      case (q, b, pos, ner) => Focus.compute(q, b, pos, ner)
    })

    // Zippers
    val zip1stStep = b.add(ZipWith[QueryRef, List[String], List[PosLabel], Map[String, String],
      (QueryRef, List[String], List[PosLabel], Map[String, String])](
        (q: QueryRef, b: List[String], pos: List[PosLabel], ner: Map[String, String]) =>
          (q, b, pos, ner)))

    val zipQuestionData = b.add(ZipWith[String, List[String], QuestionType, AnswerType,
      List[String], List[String], List[PosLabel], Map[String, String], QuestionData](
        (query: String, bow: List[String], qtype: QuestionType, atype: AnswerType,
          topic: List[String], focus: List[String], pos: List[PosLabel], ner: Map[String, String]) =>
          QuestionData(query, bow, qtype, atype, topic, focus, pos, ner)))

    val zipQuestionRef = b.add(ZipWith[Long, QuestionData, ActorRef, QuestionRef](
      (id: Long, data: QuestionData, sender: ActorRef) =>
        QuestionRef(id, data, sender)))

    // Broadcast
    val bcastStart = b.add(Broadcast[QueryRef](1))
    val bcast = b.add(Broadcast[QueryRef](5))
    val bcastBow = b.add(Broadcast[List[String]](4))
    val bcastPos = b.add(Broadcast[List[PosLabel]](2))
    val bcastNer = b.add(Broadcast[Map[String, String]](2))
    val bcast1stStep = b.add(Broadcast[(QueryRef, List[String], List[PosLabel], Map[String, String])](4))

    // Analysis graph
    bcastStart ~> signs  ~> bcast
                            bcast ~> zip1stStep.in0
                            bcast ~> query ~> zipQuestionData.in0
                            bcast ~> bow   ~> bcastBow ~> zipQuestionData.in1
                                              bcastBow ~> pos ~> bcastPos ~> zipQuestionData.in6
                                                                 bcastPos ~> zip1stStep.in2
                                              bcastBow ~> ner ~> bcastNer ~> zipQuestionData.in7
                                                                 bcastNer ~> zip1stStep.in3
                                              bcastBow ~> zip1stStep.in1
                                                          zip1stStep.out ~> bcast1stStep
                                                                            bcast1stStep ~> qtype ~> zipQuestionData.in2
                                                                            bcast1stStep ~> atype ~> zipQuestionData.in3
                                                                            bcast1stStep ~> topic ~> zipQuestionData.in4
                                                                            bcast1stStep ~> focus ~> zipQuestionData.in5

    bcast ~> id ~> zipQuestionRef.in0
    zipQuestionData.out ~> zipQuestionRef.in1
    bcast ~> sender ~> zipQuestionRef.in2
    zipQuestionRef.out ~> reportFlow ~> merge

    (bcastStart.in, merge.out)
  }
}
