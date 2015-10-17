package elementary.glue


//import jasper._
import jpl._
import scala.collection.JavaConversions


//class PrologInterop(sp: SICStus) extends Interop {
class PrologInterop extends Interop {
  //! Loads a prolog script file
  def loadScript(script: String): Boolean = {
    try {
      println(s"QUERY: consult('$script').")
      val query = new Query(s"consult('$script').")
      println("QUERY EXECUTED")
      query.hasSolution()
    }
    catch {
      case e: Throwable => {
        e.printStackTrace
          false
      }
    }
  }

  /**
  * Evaluates the given prolog statements
  * @return true if the statements were evaluated correctly, otherwise false
  **/
  def evaluate(statements: String): Boolean = {
    try {
      val query = new Query(statements)
      query.hasSolution()
    }
    catch {
      case _: Throwable => false
    }
  }

  /**
  * Defines a query statement and returns values as map
  **/
  def query(q_statement: String): Option[List[Map[String, String]]] = {
    try {
      val query = new Query(q_statement)
      if (query.hasSolution()) {
        //process: get all solutions as array of hashtables
        val tables = query.allSolutions.map(JavaConversions.mapAsScalaMap(_).map(kv => (kv._1.toString, kv._2.toString)).toMap).toList
        //process: convert them to java
        /*@annotation.tailrec
        def rec(ls: List[Map], res: List[Map[String, String]]): List[Map[String, String]] = ls match {
          case head :: tail => {
            val data = head.toMap
            val res2 = res :+ data
            rec(tail, res2)
          }
          case Nil => res
        }
        Some(rec(tables, List()))*/
        Some(tables)
      }
      else
        None
    }
    catch {
      case _: Throwable => None
    }
  }

  /**
  * Checks if the given statement is valid
  **/
  def check(statement: String): Boolean = {
    evaluate(statement)
  }

  def close() = {
    //TODO: free mem
  }
}

//! Companion object for the PrologInterop
object PrologInterop {
  //! Creates a new Prolog Interop object
  def create: Option[PrologInterop] = {
    try {
      Some(new PrologInterop())
    }
    catch {
      case _: Throwable => None
    }
  }
}
