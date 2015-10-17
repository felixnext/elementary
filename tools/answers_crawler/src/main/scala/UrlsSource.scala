package elementary.tools.answerscrawler

import akka.actor._
import akka.stream.actor._

class UrlsSource extends Actor with ActorPublisher[String]{
  import ActorPublisherMessage._
  var items: List[String] = List.empty
  var cache: List[String] = List.empty

  def receive = {
    case s:String =>
      if (!(cache contains s)) {
        cache = cache :+ s
        if (totalDemand == 0)
          items = items :+ s
        else
          onNext(s)
      }

    case Request(demand) =>
      Thread.sleep(300L)
      if (demand > items.size) {
        items foreach (onNext)
        items = List.empty
      }
      else {
        val (send, keep) = items.splitAt(demand.toInt)
        items = keep
        send foreach (onNext)
      }

    case other =>
      println(s"got other $other")
  }
}
