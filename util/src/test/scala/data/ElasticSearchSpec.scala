import org.scalatest._
import org.scalatest.Matchers
import elementary.util.data.ElasticSearchFacade
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.SearchDefinition
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.search.SearchHit

class ElasticSpec extends FlatSpec with Matchers with ElasticSearchFacade {

  "ElasticSearch" should "have connection" in {
    val recv = executeQuery{ search in "basecorpus"->"doc" }
    assert(0 < recv.await.getHits.totalHits())
  }

  "ElasticSearch" should "find entities" in {
    (true)
  }
}
