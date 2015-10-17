//import akka.event.NoLogging
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Flow
import org.scalatest._
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import slick.driver.MySQLDriver.api._
import slick.driver.MySQLDriver.backend.Database
//import kamon.Kamon

import elementary.statistic.views.AllRoutes

class AllRoutesRESTSpec extends FlatSpec with Matchers with BeforeAndAfter with ScalatestRouteTest {
  import scala.language.postfixOps

  "All Routes of the API" should "respond with information" in {
    Get("/info") ~> AllRoutes.route ~> check {
      status shouldBe OK
      contentType shouldBe `application/json`
      responseAs[String] shouldBe "{name: 'Elementary Statistic Framework', version: '1.0'}"
    }
  }

  it should "fail for not available paths" in {
    Get("/not/a/path") ~> AllRoutes.route ~> check {
      status shouldBe NotFound
      responseAs[String] shouldBe "Path is not available"
    }
    Get("/infos") ~> AllRoutes.route ~> check {
      status shouldBe NotFound
      responseAs[String] shouldBe "Path is not available"
    }
  }
}
