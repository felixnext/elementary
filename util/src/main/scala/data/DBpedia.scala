package elementary.util.data

import com.hp.hpl.jena.query.QuerySolution
import com.typesafe.config.ConfigFactory
import scala.concurrent.{Future, Promise}
import scala.util.{Success, Failure}

//! Defines access to the dbpedia database
object DBpedia extends VirtuosoFacade {
  import scala.concurrent.ExecutionContext.Implicits.global

  //! test functions
  def ofType(typeString: String, clause: String = ""): Future[Option[List[String]]] = {
    // create the query
    val query = s"""$prefixAll
      |SELECT DISTINCT *
      |WHERE { ?name rdf:type $typeString. $clause }""".stripMargin

    // execute the query and match results
    executeSparql(query).map( opt => opt.map(results => resultMap(results)( sol => sol.get("name").toString )) )
  }
}
