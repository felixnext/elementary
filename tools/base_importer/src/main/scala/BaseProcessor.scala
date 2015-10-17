package elementary.tools.baseimporter

import java.io.File
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.source.Indexable
import elementary.util.data.{ElasticSearchFacade, EntityRef}
import elementary.util.machinelearning.MLTools
import elementary.util.machinelearning.TextExtraction._
import org.elasticsearch.search.SearchHit
import org.elasticsearch.index.query.QueryBuilders
import com.sksamuel.elastic4s.{SimpleFieldValue, ArrayFieldValue, NestedFieldValue}
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import collection.JavaConversions._

object BaseProcessor {
  import scala.concurrent.ExecutionContext.Implicits.global

  // defines the indexable element to create the json
  implicit object StringIndexable extends Indexable[String] {
    override def json(t: String): String = t
  }
  case class Doc(id: Int, text: String, department: String, typ: String, module: String, offset: Int)

  // parses the base xml data
  @annotation.tailrec
  def indexDocs(f: File, id: Int, retry: Int = 0)(implicit client: ElasticClient): Unit = {
    import scala.collection.concurrent.Map
    import scala.xml.XML

    println(f.getName)
    val xml = XML.loadFile(f)
    val text = xml \\ "text"

    //parse metadata
    val meta = (xml \\ "keywords" \\ "list" \\ "item").toList.map(item => {
      val k = item \ "@n"
      val v = item \\ "item"
      (k.toString, v.text.toString)
    }).toMap

    //flatten the text contained in the doc (get rid of all tags inside)
    val flat = (text \\ "u").map(par => par.toString.replaceAll("""<.+?>\s*""", "")).foldLeft("")(_ + _).replaceAll("[ ]*(\r)?\n[ ]*", " ")

    //execute ElasticSearch query to add the doc to the index
    val fut = client.execute { index into "basecorpus" -> "doc" fields (
      "id" -> id,
      "name" -> f.getName,
      "speechevent" -> meta("speechevent"),
      "module" -> meta("module"),
      "partlevel" -> meta("partlevel"),
      "acaddept" -> meta("acaddept"),
      "acaddiv" -> meta("acaddiv"),
      "text" -> flat
    )}
    Await.ready(fut, Duration.Inf).value match {
      case Some(Success(_)) => println(s"completed doc $id")
      case _ =>
        println(s"failed doc $id - try ${retry + 1}")
        if (retry < 2) {
          Thread.sleep(500)
          indexDocs(f, id, retry + 1)
        }
    }
  }

  // calculate additional features for the document and index it
  def updateDocs(snippets: Boolean = false)(implicit client: ElasticClient) = {
    val db = if(snippets) "snippets" else "transcripts"
    val tools = new MLTools()
    object EsFct extends ElasticSearchFacade  // use mixin to make trait usable
    def extractDoc(hit: SearchHit): Doc = {
      val map = hit.sourceAsMap
      Doc(map.get("id").toString.toInt, map.get("text").toString, map.get("acaddept").toString,
        map.get("acaddiv").toString, map.get("module").toString, 0)
    }
    def gatherData(doc: Doc): Future[Any] = {
      Future(insertDoc(doc))
    }

    def insertDoc(doc: Doc, retry: Int = 0): Unit = {
      // create the document
      val docType = if (doc.typ == "ls") "lecture" else if(doc.typ == "ss") "seminar" else "other"
      val offset = if (snippets) s""" "offset": ${doc.offset}, """ else ""
      val query = s"""{
      |  "id": "${doc.id}", "corpus": "base",
      |  "module": "${doc.module}", "department": "${doc.department}", "title": "",
      |  "type": "${docType}", "text": "${doc.text.replaceAll("[ ]*(\r)?\n[ ]*", " ")}",
      |  $offset
      |  "clues": [],
      |  "vectors": {}
      |}""".stripMargin

      // execute the client data
      val fut = client.execute { index into db -> "doc" source { query }}
      Await.ready(fut, Duration.Inf).value match {
        case Some(Success(_)) => println(s"completed preprocess doc ${doc.id}")
        case _ =>
          println(s"failed preprocess doc ${doc.id} - try ${retry + 1}")
          if (retry < 2) {
            Thread.sleep(500)
            insertDoc(doc, retry + 1)
          }
      }
    }

    // splits a document into a list of snippets
    def splitDoc(doc: Doc): List[Doc] = {
      @annotation.tailrec
      def find(text: String, count: Int, offset: Int = 0): Int = {
        if(count <= 0 || text.length == 0) offset
        else {
          val pos1 = text.indexOf(" ")
          val pos2 = if(pos1 >= 0) pos1 + 1 else text.length
          find(text.substring(pos2), count - 1, offset + pos2)
        }
      }

      @annotation.tailrec
      def loop(text: String, offset: Int = 0, res: List[Doc] = List()): List[Doc] = {
        val pos = find(text, 100)
        if(pos + 1 >= text.length) res :+ Doc(doc.id, text, doc.department, doc.typ, doc.module, offset)
        else {
          val cut = find(text, 75)
          loop(text.substring(cut), offset + cut, res :+ Doc(doc.id, text.substring(0,pos), doc.department, doc.typ, doc.module, offset))
        }
      }
      loop(doc.text)
    }

    // count documents
    val docs: Long = (Helper.retryFut(client.execute{ count from "basecorpus" / "doc" }, 3).await).getCount()
    val offset: Long = 1
    val currentFut = Helper.retryFut(client.execute{ search in s"$db/doc" query { termQuery("corpus", "base") } }, 3)
    def conv(hit: SearchHit): (Int, Int) = {
      val map: collection.mutable.Map[String, Object] = hit.sourceAsMap
      (map.getOrElse("id", "0").toString.toInt, map.getOrElse("offset", "0").toString.toInt)
    }
    val current: List[(Int, Int)] = Await.ready(EsFct.resultMap(currentFut)(conv), Duration.Inf).value match {
      case Some(Success(ls)) => ls
      case _ => List()
    }

    println(s"Found Docs: $docs / Existing $db: ${current.size}")

    // iterate through all current documents
    def updateBatch(startCount: Long, count: Long): Future[Unit] = {
      val data = Helper.retryFut(client.execute{ search in "basecorpus/doc" start startCount.toInt limit count.toInt }, 3)
      EsFct.resultMap(data)(extractDoc).flatMap{ ls => {
        val lsDoc = if(snippets) ls.flatMap(splitDoc(_)) else ls
        val lsCur = lsDoc.filterNot(doc => current.contains((doc.id, doc.offset)))
        val lsFut = lsCur.map(gatherData(_))
        val fut = Future sequence lsFut
        fut.map(_ => Unit)
      }}
    }

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

  def deletePre(db: String)(implicit client: ElasticClient) =
    Helper.retryFut(client.execute{ delete from db / "doc" where { termQuery("corpus", "base") } }, 3).await
  def deleteDocs(implicit client: ElasticClient) =
    Helper.retryFut(client.execute{ delete from "basecorpus" / "doc" where matchall }, 3).await
}
