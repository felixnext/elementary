package elementary.util.data

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import scala.concurrent.Future

//! Defines access to the DBpedia entity index
object DBpediaIndex extends ElasticSearchFacade {
  import scala.concurrent.ExecutionContext.Implicits.global

  //! searches for dbpedia entities containing the given name (returns possible matches with scoring)
  def searchEntity(name: String): Future[List[(String, Double)]] = {
    val results = executeQuery{ search in "dbpediaabstractname" -> "entity" query name }
    resultMap(results)(sr => (sr.sourceAsMap.get("entity").toString, sr.getScore().toDouble))
  }
}
