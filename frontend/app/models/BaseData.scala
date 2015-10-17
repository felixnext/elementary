package models

import play.api.libs.json._

import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.SearchDefinition
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.SearchHits
import org.elasticsearch.common.settings.ImmutableSettings

import scala.concurrent._
import scala.compat.Platform.currentTime
import scala.util.{Try, Success, Failure}

case class BaseData(speechevent : String, module : String, name : String, partlevel : String, text : String)

object BaseData {
  import play.api.Play.current

  lazy val client = {
    val settings = ImmutableSettings.settingsBuilder().put("cluster.name", "elementary").build()
    ElasticClient.remote(
      settings,
      ElasticsearchClientUri(current.configuration.getString("elasticsearch.address").getOrElse("elasticsearch://is62.idb.cs.tu-bs.de:9300"))
    )
  }

  def getRandom() : Future[BaseData] = {
    getRandom(currentTime)
  }

  def getRandom(seed: Long) : Future[BaseData] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    executeQuery{
      search in "basecorpus" -> "doc" limit 1 rawQuery
      s"""{
        "function_score": {
          "functions": [{
            "random_score": {
              "seed": ${seed}
            }
          }]
        }
      }"""
    }.map(r => {
      val doc = r.getHits.getAt(0).sourceAsMap

      BaseData(
        doc.get("speechevent").toString,
        doc.get("module").toString,
        doc.get("name").toString,
        doc.get("partlevel").toString,
        doc.get("text").toString)
    })
  }

  private def executeQuery(queryString: SearchDefinition): Future[SearchResponse] = {
    client.execute { queryString } 
  }

}
