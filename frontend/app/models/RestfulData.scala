package models

import play.api.libs.ws._
import play.api.libs.json._
import scala.concurrent.Future

trait RestfulModel {
    val host : String;
    val port : String;

    def resultMap[T](result : Future[JsResult[T]]) : Future[T] = {
      import scala.concurrent.ExecutionContext.Implicits.global

      result.map {
        fr => fr.get
      }
    }
}
