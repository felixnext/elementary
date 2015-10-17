package elementary.pipeline.candidates

import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{FlowGraph, Broadcast, Flow, ZipWith, Merge}
import com.typesafe.config.ConfigFactory
import elementary.util.common.Communication._
import elementary.pipeline.PipeCommunication._
import elementary.util.machinelearning._
import elementary.util.data.{Transcript, Snippets, Transcripts}
import scala.concurrent.{Future, Await}
import scala.concurrent.duration.Duration
import scala.util.{Success, Failure, Try}

class Word2VecSynthesizer(w2v: Word2Vec, model: String, focusWord: Boolean = false) extends Synthesizer {
  import scala.concurrent.ExecutionContext.Implicits.global

  // load the stopwords
  val config = ConfigFactory.load()
  val stopwords: List[String] = Try(
    scala.io.Source.fromFile(config.getString("elementary.parameter.general.stopwords")).getLines().toList
  ).toOption.getOrElse(List())


  // load doc2vec model
  val vectors: List[(String, Array[Double])] = {
    // get aLL transcripts from elastic elastis Search (NOTE: this will be around 90.000 docs / might use pages)
    val futVecs = Transcript.getAll(Snippets).map( ls =>
      ls.map(
        tscript => (tscript.docId, w2v.paragraph(tscript.text))
      ).filter(tpl => tpl._2 != None).map(tpl => (tpl._1, tpl._2.get))
    )
    // await for the results
    Await.result(futVecs, Duration.Inf)
  }

  // count the difference length of the items
  val dimensions = vectors.groupBy(_._2.length).map(tpl => tpl._1 -> tpl._2.size)
  val configuration: Map[String, String] = Map("name" -> "Word2Vec", "focusWord" -> focusWord.toString, "model" -> model,
    "vectors" -> vectors.size.toString, "dimensions" -> dimensions.toString)

  // defines the flow for the word2vec candidate system
  lazy val flow = Flow() { implicit b =>
    import FlowGraph.Implicits._

    // define the start and endpoints
    val bcast = b.add(Broadcast[ExtractionRef](2))
    val zipping = b.add(ZipWith[ExtractionRef, Future[List[CandidateData]], CandidateRef] {
      (ref, cands) =>
        CandidateRef(ref.id, ref.data, ref.docs, cands, ref.sender)
    })

    // define the flow to work with
    val passThrough = Flow[ExtractionRef]
    // create the vector for the question based on the selected option
    val question    =
      if(!focusWord) Flow[ExtractionRef].map(ref => w2v.centroid( ref.data.bow.filter( !stopwords.contains(_) ) ))
      else           Flow[ExtractionRef].map(ref => VectorMath.avgArray(  ref.data.focus.map(  w2v.paragraphTolerant(_) ), dimensions.keys.max ))
    // map the document and rank them for the score
    val synthesize  = Flow[Option[Array[Double]]].map( arrOpt => arrOpt match {
      case Some(arr) =>
        // get a list of extraction data as future
        ( Future sequence w2v.nearestNeighborItems(arr, vectors).map(item => {
          // load the item with the given id and convert it to candidate data
          val trans = Transcript.getId(item._1, Snippets)
          trans.map(_.map(t => {
            val topics = List() //w2v.nearWordsText(t.text, 5)
            CandidateData(s"""[id: ${t.id}, corpus: ${t.corpus}, department: ${t.department}, module: ${t.module},
              | topics: ${topics}]\n${t.text}""".stripMargin,
              item._2, t.offset, t.docId)
          }))
        }) ).map(_.filter(_ != None).map(_.get).sortWith(_.ranking > _.ranking))
      case None => Future(List())
    })

    // outline the actual flow
    bcast ~> passThrough            ~> zipping.in0
    bcast ~> question ~> synthesize ~> zipping.in1

    (bcast.in, zipping.out)
  }
}

// companion to create the synthesizer
object Word2VecSynthesizer {
  def create(model: String, focusWord: Boolean = false): Either[Throwable, Word2VecSynthesizer] = {
    Doc2VecPy.create(model) match {
      case Right(d2v) => Right(new Word2VecSynthesizer(d2v, model, focusWord))
      case Left(e)    => Left(e)
    }
  }

  def createOpt(model: String, focusWord: Boolean = false): Option[Word2VecSynthesizer] = create(model, focusWord) match {
    case Right(syn)   => Some(syn)
    case Left(_)      => None
  }
}
