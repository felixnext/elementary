package elementary.util.data

import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{SearchDefinition, GetDefinition, CountDefinition}
import com.typesafe.config.ConfigFactory
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.count.CountResponse
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.SearchHits
import org.elasticsearch.common.settings.ImmutableSettings
import java.util.Iterator
import scala.concurrent._

case class ElasticDocument(text: String, ranking: Double)

trait ElasticSearchFacade {
  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val client = {
    val config = ConfigFactory.load()
    val settings = ImmutableSettings.settingsBuilder().put("cluster.name", "elementary").build()
    ElasticClient.remote(
      settings,
      ElasticsearchClientUri(config.getString("elementary.util.data.elasticsearch.address"))
    )
  }

  // execute an ElasticSearch Query
  def executeQuery(queryString: SearchDefinition): Future[SearchResponse] = {
    client.execute { queryString }
  }

  // execute an ElasticSearch Query
  def executeQuery(queryString: GetDefinition): Future[GetResponse] = {
    client.execute { queryString }
  }

  // execute an ElasticSearch Query
  def executeQuery(queryString: CountDefinition): Future[CountResponse] = {
    client.execute { queryString }
  }

  // iterate through a set of SearchHits
  def resultMap[T](results: Future[SearchResponse])(f: SearchHit => T): Future[List[T]] = {
    // loop
    @annotation.tailrec
    def loop(results: java.util.Iterator[SearchHit], ls: List[T] = Nil): List[T] = {
      if(results.hasNext) {
        val res = results.next()
        loop(results, ls :+ f(res))
      }
      else {
        ls
      }
    }

    results.map({
      resp => loop(resp.getHits.iterator)
    })
  }
}
