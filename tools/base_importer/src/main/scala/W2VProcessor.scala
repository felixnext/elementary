package elementary.tools.baseimporter

import java.io.File
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.source.Indexable
import elementary.util.data.{ElasticSearchFacade, EntityRef}
import elementary.util.machinelearning.{Word2Vec, Word2VecLocal}
import elementary.util.machinelearning.MLTools
import elementary.util.machinelearning.LanguageFeatures._
import elementary.util.machinelearning.TextExtraction._
import org.elasticsearch.search.SearchHit
import org.elasticsearch.index.query.QueryBuilders
import com.sksamuel.elastic4s.{SimpleFieldValue, ArrayFieldValue, NestedFieldValue}
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.util.{Success, Failure, Try}

object W2VProcessor {
  import scala.concurrent.ExecutionContext.Implicits.global

  case class Doc(id: String, text: String)

  // calculate additional features for the document and index it
  def updateDocs(name: String, model: String, db: String, unvec: Boolean = false)(implicit client: ElasticClient) = {
    // load the vector model
    val w2v: Option[Word2Vec] = Word2VecLocal.load(model) match { case Right(model) => Some(model) case Left(e) => None }
    if (w2v.isDefined) println("Word2Vec model loaded")
    else println(s"Error loading model '$model'")

    // get the machine learning tools
    val tools = new MLTools()
    object EsFct extends ElasticSearchFacade  // use mixin to make trait usable

    def extractDoc(hit: SearchHit): Doc = {
      val map = hit.sourceAsMap
      Doc(hit.getId(), map.get("text").toString)
    }

    def gatherData(doc: Doc): Future[Unit] = Future {
      val ls: List[String] = tools.wordCount(doc.text).toList.flatMap(tpl => List.fill(tpl._2)(tpl._1))
      //val ls: List[String] = tools.bagOfWords(doc.text).toList
      val vector = w2v.get.centroidTolerant(ls)
      insertDoc(doc, vector)
    }

    def insertDoc(doc: Doc, vector: Array[Double], retry: Int = 0): Unit = {
      // execute the client data
      val vecs = Try(vector.foldLeft("")((old, value) => old + "," + value).substring(1)).toOption.getOrElse("")
      val fut = Helper.postUpdate(db, doc.id, "vectors", "vec", true, s""" "vec": {"$name": [$vecs]} """)
      def execRetry = { println(s"failed vectorize transcript ${doc.id} - try ${retry + 1}")
        if (retry < 2) {
          Thread.sleep(500)
          insertDoc(doc, vector, retry + 1)
        } }
      Await.ready(fut, Duration.Inf).value match {
        case Some(Success(_)) => println(s"completed preprocess doc ${doc.id}")
        case Some(Failure(e)) => { println(s"Failed with: $e"); execRetry }
        case _ => execRetry
      }
    }

    // iterate through all current documents
    def updateBatch(startCount: Long, count: Long): Future[Unit] = {
      val data = Helper.retryFut(client.execute{ search in s"$db/doc" start startCount.toInt limit count.toInt }, 3)
      EsFct.resultMap(data)(extractDoc).flatMap{ ls => {
        val lsFut =
          if (unvec) ls.map(doc =>
            Helper.retryFut(Helper.postUpdate(db, doc.id, "vectors", "vec", false, """ "vec": {} """), 3).map(d => ())
          )
          else ls.map(gatherData(_))
        val fut = Future sequence lsFut
        fut.map(_ => Unit)
      }}
    }

    // count documents
    val docs: Long = (Helper.retryFut(client.execute{ count from db / "doc" }, 3).await).getCount()
    val offset: Long = 5

    println(s"Found Transcripts in '$db': $docs")

    @annotation.tailrec
    def loopDocs(start: Long, fut: Future[Unit]): Future[Unit] = {
      val (end, complete) = if(start+offset <=  docs) (offset, false) else ((docs-start), true)
      val fut2 = fut.flatMap(_ => { println(s"Processed: $start of $docs"); updateBatch(start, end) } )
      if (complete) fut2
      else loopDocs(start+end, fut2)
    }

    if (w2v.isDefined || unvec) {
      val fut = loopDocs(0L, Future(Unit))
      fut.onComplete {
        case Success(_) =>
        case Failure(e) => println(s"Processing failed with: $e")
      }
      Await.ready(fut, Duration.Inf)
    }
    else println("Abort this model")
  }

  // iterate through all models
  def vectorize(db: String, file: String)(implicit client: ElasticClient) = {
    // all models that shall be indexed
    val models: List[(String, String)] = scala.io.Source.fromFile(file).getLines
      .toList.map(file => Try((file.split(";")(0), file.split(";")(1))).toOption)
      .filter(_ != None).map(_.get)
    // iterate through all models
    models.foreach(tpl => {
      updateDocs(tpl._1, tpl._2, db)
      println(s"completed model '${tpl._1}'")
    })
  }

  def unvectorize(db: String)(implicit client: ElasticClient) =
    updateDocs("", "", db, true)

  def clue(db: String, unclue: Boolean = false)(implicit client: ElasticClient) = {
    // get the machine learning tools
    val tools = new MLTools()
    object EsFct extends ElasticSearchFacade  // use mixin to make trait usable

    def extractDoc(hit: SearchHit): Doc = {
      val map = hit.sourceAsMap
      Doc(hit.getId(), map.get("text").toString)
    }
    def gatherData(doc: Doc): Future[Unit] = {
      val entities = Helper.retryFut(tools.detectDBpediaEntities(doc.text), 3)
      entities.map(ent => insertDoc(doc, ent))
    }

    def insertDoc(doc: Doc, entities: List[EntityRef], retry: Int = 0): Unit = {
      // execute the client data
      val cluesRaw = entities.map(t => {
        val typesRaw = t.types.foldLeft("")(_ + ", \"" + _ + "\"")
        val types = if(typesRaw.length > 2) typesRaw.substring(2) else ""
        s"""{
        |"id": "${t.uri}", "type": "entity", "probability": ${t.probability},
        |"start": ${t.start}, "length": ${t.length}, "surfaceForm": "${t.surfaceForm}",
        |"types": [$types]}""".stripMargin
      }).foldLeft("")(_ + "," + _)
      val clues = if(cluesRaw.length > 1) cluesRaw.substring(1) else ""
      val fut = Helper.postUpdate(db, doc.id, "clues", "clue", true, s""" "clue": [$clues] """)
      def execRetry = { println(s"failed vectorize transcript ${doc.id} - try ${retry + 1}")
        if (retry < 2) {
          Thread.sleep(500)
          insertDoc(doc, entities, retry + 1)
        } }
      Await.ready(fut, Duration.Inf).value match {
        case Some(Success(_)) => println(s"completed preprocess doc ${doc.id}")
        case Some(Failure(e)) => { println(s"Failed with: $e"); execRetry }
        case _ => execRetry
      }
    }

    // iterate through all current documents
    def updateBatch(startCount: Long, count: Long): Future[Unit] = {
      val data = Helper.retryFut(client.execute{ search in s"$db/doc" start startCount.toInt limit count.toInt }, 3)
      EsFct.resultMap(data)(extractDoc).flatMap{ ls => {
        val lsFut =
          if (unclue) ls.map(doc =>
            Helper.retryFut(Helper.postUpdate(db, doc.id, "clues", "clue", false, """ "clue": [] """), 3).map(d => ())
          )
          else ls.map(gatherData(_))
        val fut = Future sequence lsFut
        fut.map(_ => Unit)
      }}
    }
    // count documents
    val docs: Long = (Helper.retryFut(client.execute{ count from db / "doc" }, 3).await).getCount()
    val offset: Long = 5
    println(s"Found Transcripts in '$db': $docs")

    @annotation.tailrec
    def loopDocs(start: Long, fut: Future[Unit]): Future[Unit] = {
      val (end, complete) = if(start+offset <=  docs) (offset, false) else ((docs-start), true)
      val fut2 = fut.flatMap(_ => { println(s"Processed: $start of $docs"); updateBatch(start, end) } )
      if (complete) fut2
      else loopDocs(start+end, fut2)
    }

    val fut = loopDocs(0L, Future(Unit))
    fut.onComplete {
      case Success(_) =>
      case Failure(e) => println(s"Processing failed with: $e")
    }
    Await.ready(fut, Duration.Inf)
  }

  def deleteTS(implicit client: ElasticClient) =
    Helper.retryFut(client.execute{ delete from "transcripts" / "doc" where matchall }, 3).await
  def deleteSNIP(implicit client: ElasticClient) =
    Helper.retryFut(client.execute{ delete from "snippets" / "doc" where matchall }, 3).await
}
