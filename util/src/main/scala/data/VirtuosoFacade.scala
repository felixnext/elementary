package elementary.util.data

import com.typesafe.config.ConfigFactory
import com.hp.hpl.jena.query._
import com.hp.hpl.jena.rdf.model.{Model, ModelFactory, Resource}
import scala.concurrent.Future
import scala.util.Try

trait VirtuosoFacade {
  import scala.concurrent.ExecutionContext.Implicits.global

  // url of the endpoint (set by sub classes)
  val endpoint = {
    val config = ConfigFactory.load()
    config.getString("elementary.util.data.virtuoso.endpoint")
  }
  QueryFactory.make()

  // stores some query part that might be useful
  val prefixRDF         = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
  val prefixRDFS        = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
  val prefixDBpediaOWL  = "PREFIX dbpedia-owl: <http://dbpedia.org/ontology/>"
  val prefixOWL         = "PREFIX owl: <http://www.w3.org/2002/07/owl#>"
  val prefixFBASE       = "PREFIX fbase: <http://rdf.basekb.com/ns/>"
  val prefixFOAF        = "PREFIX foaf: <http://xmlns.com/foaf/0.1/>"
  val prefixYAGO        = "PREFIX yago: <http://yago-knowledge.org/resource/>"
  val prefixDBpediaYAGO = "PREFIX yago: <http://dbpedia.org/class/yago/>"
  val prefixVCARD       = "PREFIX vcard: <http://www.w3.org/2001/vcard-rdf/3.0#>"
  // holds all prefixes
  val prefixAll         = s"""
    |$prefixRDF
    |$prefixRDFS
    |$prefixOWL
    |$prefixDBpediaOWL
    |$prefixDBpediaYAGO
    |$prefixFBASE
    |$prefixFOAF
    |$prefixVCARD""".stripMargin

  // iterates through a resultset using a mapping function
  @annotation.tailrec
  final def resultMap[T](results: (ResultSet, QueryExecution), ls: List[T] = Nil)(f: QuerySolution => T): List[T] = {
    if(results._1.hasNext()) {
      val res = results._1.next()
      resultMap(results, ls :+ f(res))(f)
    }
    else {
      results._2.close()
      ls
    }
  }

  // executes a sparql query on the specified endpoint
  def executeSparql(queryString: String): Future[Option[(ResultSet, QueryExecution)]] = Future {
    Try {
      val query: Query = QueryFactory.create(queryString)
      val qexec: QueryExecution = QueryExecutionFactory.sparqlService(endpoint, query)
      (qexec.execSelect(), qexec)
    }.toOption
  }
}
