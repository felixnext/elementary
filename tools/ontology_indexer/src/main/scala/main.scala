package elementary.tools.ontologyindexer

import elementary.util.data.{ElasticSearchFacade, VirtuosoFacade}
import com.typesafe.config.ConfigFactory
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.hp.hpl.jena.query.QuerySolution
import scala.concurrent.{Future, Promise}
import scala.util.{Success, Failure}

// Ontology index generation
object OntologyIndexer extends App with ElasticSearchFacade with VirtuosoFacade {
  import scala.concurrent.ExecutionContext.Implicits.global

  println("Ontology Index Generation started...")
  println("use '-mode [extract|extractfull]' to specify operation mode.")

  // parse arguments
  @annotation.tailrec
  def loop(argsLs: List[String], result: Map[String, String] = Map()): Map[String, String] = {
    argsLs match {
      case name :: value :: tail if name.startsWith("-") =>
        val res = result + (name.substring(1).toLowerCase -> value)
        loop(tail, res)
      case name :: value :: tail => loop(tail, result)
      case _ => result
    }
  }
  val argMap = loop(args.toList)
  val mode = argMap.getOrElse("mode", "extract").toLowerCase

  mode match {
    case "extract" => println("Starting Abstract/Name Extraction")
      loopPredicates(List(
          ("abstract", "has abstract")
          , ("name", "name")
        ), 0
      )
    case "extractfull" => println("Starting Full Extraction")
      predicateList onComplete {
       case Success(Some(ls)) => loopPredicates(ls, 0)
       case Success(_) => println("Failed to get data")
       case Failure(e) => println(e.getStackTrace)
      }
    case _ => println("Unkown operation mode")
  }

  //predicate list query
  def predicateList : Future[Option[List[(String, String)]]] = {
    val query = s"""$prefixAll
      |SELECT ?s ?o
      |WHERE {
      |?s rdfs:label ?o.
      |?s rdf:type rdf:Property.
      |FILTER (LANG(?o)='en')}
      |LIMIT 1000000
      |OFFSET 0""".stripMargin

    val promise = Promise[Option[List[(String, String)]]]()
    executeSparql(query) onComplete {
      case Success(data) => data match {
        case Some(results) =>
          promise.success( Some(resultMap(results)(sol => (sol.get("s").toString, sol.get("o").toString))) )
        case None => promise.success(None)
      }
      case Failure(e) => promise.failure(e)
    }
    promise.future
  }

  //entity list query
  def entityList(predicate : String, s : Int, retry: Int) : Future[Option[List[(String, String)]]] = {
    val pattern = "http://.*/".r
    val p = pattern.replaceFirstIn(predicate, "")

    val query = s"""$prefixAll
      |SELECT ?n ?p
      |WHERE {
      |?p dbpedia-owl:$p ?n
      |}
      |LIMIT 1000
      |OFFSET $s""".stripMargin

    val promise = Promise[Option[List[(String, String)]]]()
    executeSparql(query) onComplete {
      case Success(data) => data match {
        case Some(results) => {
          promise.success( Some(resultMap(results)(sol => (sol.get("n").toString, sol.get("p").toString))) )
        }
        case None => {
          if(retry < 4) {
            Thread.sleep(1000)
            entityList(predicate, s, retry + 1) onComplete {
              case Success(data) => promise.success(data)
              case Failure(e) => promise.failure(e)
            }
          }
          else {
            println(query)
            promise.success(None)
          }
        }
      }
      case Failure(e) => promise.failure(e)
    }
    promise.future
  }

  // loop through all the predicates, processing all entities in blocks of 1000
  //@annotation.tailrec
  final def loopPredicates(ls : List[(String, String)], offset : Int) : Unit = ls match {
    case x :: xs => {
      println("=====================================")
      println(x + " " + offset)

      // TODO (might use await in favor of tailrec)
      entityList(x._1, offset, 0) onComplete {
        case Success(data) => data match {
          case Some(ls) => {
            loopEntities(ls)
            if(ls.length >= 1000)
              loopPredicates(x :: xs, offset + 1000)
            else
              loopPredicates(xs, 0)
          }
          case None => println("no data returned")
        }
        case Failure(e) => println(e.toString)
      }
    }
    case _ => Unit
  }

  // loop through the entities, adding them to the index
  @annotation.tailrec
  final def loopEntities(ls : List[(String, String)]) : Unit = ls match {
    case x :: xs => {
      //execute ElasticSearch query to add the doc to the index
      client.execute { index into "dbpediaabstractname" -> "entity" fields (
        "key" -> x._1,
        "entity" -> x._2
      )}

      loopEntities(xs)
    }
    case _ => {}
  }
}
