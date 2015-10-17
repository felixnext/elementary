package elementary.api

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Sink, Source, Tcp }
import akka.util.ByteString
import scala.concurrent.duration.DurationConversions
import scala.util.{ Failure, Success }

/**
 * Defines a streaming echo server
 */
object StreamInApp extends App  {
  implicit val system = ActorSystem("ServerSystem")
  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  val address = "127.0.0.1"
  val port = 8888

  val handler = Sink.foreach[Tcp.IncomingConnection] { conn =>
    println("Client connected from: " + conn.remoteAddress)
    conn handleWith Flow[ByteString]
  }

  val connections = Tcp().bind(address, port)
  val binding = connections.to(handler).run()

  binding.onComplete {
    case Success(b) =>
      println("Server started, listening on: " + b.localAddress)
    case Failure(e) =>
      println(s"Server could not bind to $address:$port: ${e.getMessage}")
      system.shutdown()
  }


}
