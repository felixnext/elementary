package elementary.tools.baseimporter

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Helper {
  import scala.concurrent.ExecutionContext.Implicits.global

  def retryFut[T](f: => Future[T], retry: Int): Future[T] = Future {
    def exec = {
      Thread.sleep(500);
      println(s"Failed - Retry $retry")
      retryFut(f, retry - 1)
    }
    val x: Future[T] = Await.ready(f, Duration.Inf).value match {
      case Some(Success(res)) => Future(res)
      case Some(Failure(e)) => if (retry > 0) exec else Future.failed(e)
      case None =>
        if (retry > 0) exec
        else Future.failed(new InterruptedException("Could not execute future"))
    }
    x
  }.flatMap(x => x)

  def postUpdate(db: String, id: String, field: String, value: String, add: Boolean = false, params: String = ""): Future[String] = Future {
    import scalaj.http.Http

    val eq = if(add) "+=" else "="
    val param_full = if(params.length > 0) s""", "params": { $params }""" else ""

    Http(s"http://is62.idb.cs.tu-bs.de:9200/$db/doc/$id/_update")
      .postData(s"""{ "script": "ctx._source.$field $eq $value"$param_full }""")
      .header("Charset", "UTF-8")
      .timeout(connTimeoutMs = 2000, readTimeoutMs = 7000)
      .asString
  }.flatMap( res =>
    if(res.code != 200) Future.failed(new Exception(s"Code: ${res.code} / Value: ${res.body}"))
    else Future(res.body)
  )
}
