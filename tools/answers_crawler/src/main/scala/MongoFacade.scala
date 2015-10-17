package elementary.util.data

import scala.concurrent.{Future}

import com.typesafe.config.ConfigFactory
import reactivemongo.api._
import reactivemongo.api.MongoConnection.ParsedURI
import scala.concurrent.ExecutionContext.Implicits.global

abstract trait MongoFacade {
  // abstract vals to store connection data
  val mongoDBName: String
  val mongoCollName: String

  lazy val mongoClient = {
    // create connection to the mongo database
    val config = ConfigFactory.load("application")
    val server = new ParsedURI(
      List( (config.getString("elementary.util.data.mongodb.address"), config.getInt("elementary.util.data.mongodb.port")) ),
      MongoConnectionOptions(),
      List(),
      None,
      None
    )
    // gets an instance of the driver
    // (creates an actor system)
    val driver = new MongoDriver
    driver.connection(server)
  }

  // create connection to the mongo db database
  lazy val mongo = {
    // select the database to work on
    val dbConn = mongoClient(mongoDBName)
    // Select the collection of documents to work on
    dbConn(mongoCollName)
  }

  // Close the connection to the database
  def close() = {
    // TODO close connection?
    mongoClient.close()
  }
}
