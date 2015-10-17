package elementary.pipeline.models

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import elementary.util.data.ElasticSearchFacade

object LectureCorpora extends ElasticSearchFacade {
  // Holds the name of the main index
  val index = "docs"

  //! Retrieve a list of docs based on a bag of words
  /*def retrieveBoW(words: String): Future[List[(String, Double)]] = {
    val resp1 = client.execute {
      search in "basecorpus" query words
    }
    response.map(data => data.getHits().getHits())
  }

  //! Retrieve a list of documents based on a text
  def retrieveText(text: String): Future[String] = {
    val response = client.execute{ search in index query text }
    response.map(data => )
  }*/
}
