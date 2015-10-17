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
import scala.io.Source
import spray.json._
import collection.JavaConversions._

object AIVProcessor extends DefaultJsonProtocol {
  import scala.concurrent.ExecutionContext.Implicits.global

  // defines the indexable element to create the json
  implicit object StringIndexable extends Indexable[String] {
    override def json(t: String): String = t
  }
  case class DocTrans(id: Int, title: String, transkription: String)
  case class Doc(id: Int, title: String, text: String, department: String, offset: Int)

  implicit object DocJsonFormat extends RootJsonFormat[Option[DocTrans]] {
    def write(c: Option[DocTrans]) = JsObject( "id" -> JsString(c.get.id.toString) )
    def read(value: JsValue) = {
      value.asJsObject.getFields("id", "title", "transkription") match {
        case Seq(JsNumber(id), JsString(title), JsString(transcript)) =>
          Some( DocTrans(id.toInt, title, transcript.trim) )
        case e => None
      }
    }
  }

  // parses the base xml data
  def indexDocs(f: File)(implicit client: ElasticClient) = {
    import scala.collection.concurrent.Map
    import scala.xml.XML

    // load the data from file and parse (note: this might cost memory)
    println(s"start loading file '${f.getName}'")
    val res = Source.fromFile(f).mkString.replaceAll("[ ]*(\r)?\n[ ]*", " ").replaceAll("""\\'""", """""").replaceAll("""\t""", " ")
    println(s"loaded json file / length: ${res.length}")
    val list = res.parseJson.convertTo[List[Option[DocTrans]]].filterNot(_ == None).map(_.get)
    println(s"Loaded ${list.size} transcripts from file '${f.getName}'")

    //@annotation.tailrec
    def indexDoc(doc: DocTrans, retry: Int = 0): Unit = {
      val fut1 = Future {
        val xml = XML.loadString(doc.transkription)
        val text = xml \\ "decoded-data" \\ "text"
        //parse metadata
        val department = (xml \\  "decoded-data" \\ "general" \\ "projectname").text.toString
        //flatten the text contained in the doc (get rid of all tags inside)
        val flat = (text).map(par => par.toString.replaceAll("""<.+?>\s*""", " ")).foldLeft("")(_ + _).replaceAll("[ ]*(\r)?\n[ ]*", " ")
        (department, flat)
      }

      //execute ElasticSearch query to add the doc to the index
      val fut = fut1.flatMap( tpl => client.execute { index into "aivcorpus" -> "doc" fields (
        "id" -> doc.id, "title" -> doc.title, "department" -> tpl._1, "text" -> tpl._2
      )})
      Await.ready(fut, Duration.Inf).value match {
        case Some(Success(_)) => println(s"completed doc ${doc.id}")
        case _ =>
          println(s"failed doc ${doc.id} - try ${retry + 1}")
          if (retry < 2) {
            Thread.sleep(500)
            indexDoc(doc, retry + 1)
          }
      }
    }

    list.foreach(doc => indexDoc(doc))
  }

  // calculate additional features for the document and index it
  def updateDocs(snippets: Boolean = false)(implicit client: ElasticClient) = {
    val db = if(snippets) "snippets" else "transcripts"
    val tools = new MLTools()
    object EsFct extends ElasticSearchFacade  // use mixin to make trait usable
    def extractDoc(hit: SearchHit): Doc = {
      val map = hit.sourceAsMap
      Doc(map.get("id").toString.toInt, map.get("title").toString,
        map.get("text").toString.replaceAll("[ ]+", " "), map.get("department").toString, 0)
    }
    def gatherData(doc: Doc): Future[Any] = {
      Future(insertDoc(doc))
    }

    def insertDoc(doc: Doc, retry: Int = 0): Unit = {
      // create the document
      val offset = if (snippets) s""" "offset": ${doc.offset}, """ else ""
      val query = s"""{
      |  "id": "${doc.id}", "corpus": "aiv",
      |  "module": "", "department": "${doc.department}", "title": "${doc.title}",
      |  "type": "lecture", "text": "${doc.text.replaceAll("[ ]*(\r)?\n[ ]*", " ")}",
      |  $offset "clues": [], "vectors": {}
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
        if(pos + 1 >= text.length) res :+ Doc(doc.id, doc.title, text, doc.department, offset)
        else {
          val cut = find(text, 75)
          loop(text.substring(cut), offset + cut, res :+ Doc(doc.id, doc.title, text.substring(0,pos), doc.department, offset))
        }
      }
      loop(doc.text)
    }

    // count documents
    val docs: Long = (Helper.retryFut(client.execute{ count from "aivcorpus" / "doc" }, 3).await).getCount()
    val offset: Long = 1
    val currentFut = Helper.retryFut(client.execute{ search in s"$db/doc" query { termQuery("corpus", "aiv") } }, 3)
    def conv(hit: SearchHit): (Int, Int) = {
      val map: collection.mutable.Map[String, Object] = hit.sourceAsMap
      (map.getOrElse("id", "0").toString.toInt, map.getOrElse("offset", "0").toString.toInt) }
    val current: List[(Int, Int)] = Await.ready(EsFct.resultMap(currentFut)(conv), Duration.Inf).value match {
      case Some(Success(ls)) => ls
      case _ => List()
    }
    println(s"Found Docs: $docs / Existing $db: ${current.size}")

    // iterate through all current documents
    def updateBatch(startCount: Long, count: Long): Future[Unit] = {
      val data = Helper.retryFut(client.execute{ search in "aivcorpus/doc" start startCount.toInt limit count.toInt }, 3)
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
    Helper.retryFut(client.execute{ delete from db / "doc" where { termQuery("corpus", "aiv") } }, 3).await
  def deleteDocs(implicit client: ElasticClient) =
    Helper.retryFut(client.execute{ delete from "aivcorpus" / "doc" where matchall }, 3).await
}
