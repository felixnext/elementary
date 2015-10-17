package elementary.tools.baseimporter

import java.io.File
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.source.Indexable
import org.elasticsearch.search.SearchHit
import org.elasticsearch.index.query.QueryBuilders
import com.sksamuel.elastic4s.{SimpleFieldValue, ArrayFieldValue, NestedFieldValue}
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import elementary.util.data.ElasticSearchFacade
import elementary.util.machinelearning.MLTools
import elementary.util.machinelearning.TextExtraction._

object WikiProcessor {
  import scala.concurrent.ExecutionContext.Implicits.global

  // parse the wikipedia xml data and index into elastic search
  def indexDocs(f: File)(implicit client: ElasticClient) = {
    import scala.collection.concurrent.Map
    import scala.xml.XML

    println(f.getName)
    val xml = XML.loadFile(f)
    val text = xml \\ "text"

    // TODO parse data

    // index into ES
    /*client.execute { index into "wikipedia" -> "article" fields (
      "name" -> f.getName,
      "speechevent" -> meta("speechevent"),
      "module" -> meta("module"),
      "partlevel" -> meta("partlevel"),
      "acaddept" -> meta("acaddept"),
      "acaddiv" -> meta("acaddiv"),
      "text" -> flat
    )}.await*/
  }

  def deleteDocs(implicit client: ElasticClient) = {
    client.execute{ delete from "wikipedia" / "article" where matchall }.await
  }
}
