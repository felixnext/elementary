package elementary.tools.answerscrawler

import akka.actor._
import akka.stream.actor._
import akka.stream.scaladsl._
import akka.stream.ActorMaterializer

import net.fehmicansaglam.bson.BsonDocument

//import edu.arizona.sista.processors.*;
//import edu.arizona.sista.processors.corenlp.CoreNLPProcessor;

object QuestionAnalysis extends App with TepkinFacade {
  implicit val system = ActorSystem("test")
  implicit val materializer = ActorMaterializer()

  val g = FlowGraph.closed() { implicit b: FlowGraph.Builder[Unit] =>
    import FlowGraph.Implicits._

    val question = Flow[BsonDocument].map(b => { b: Question })
    val ask = Flow[Question].map(q => IOFlow.askYesNo(q))
    val annotate = Flow[Question].map(q => AnnotateFlow.annotateWhWord(q))
    val pos = Flow[Question].map(q => NLPFlow.posTag(q))

    val printsink = Sink.foreach[Question](x => println("####### " + x.id + "#######"))
    val foldsink = Sink.fold[Map[String, Int], Question](Map[String, Int]())(
      (x, y) => FoldFlow.count(x, y))
    val writesink = Sink.foreach[Question](q => IOFlow.writeFile(q))

    srcMongo ~> question ~> pos ~> printsink
  }

  g.run()
}
