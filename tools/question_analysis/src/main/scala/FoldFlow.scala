package elementary.tools.answerscrawler

object FoldFlow {
  //count categories etc. of questions
  def count(x: Map[String, Int], y: Question) : Map[String, Int] = {
    if(y.category.contains("Science & Mathematics")) {
      print(y.id)
      println(x)
      val map : Map[String, Int] = x ++ Map(y.category.last -> (x.getOrElse(y.category.last, 0).toInt + 1))
      val taken = try {
        y.taken match {
          case Some(t) => if(t) {Map("taken" -> (x.getOrElse("taken", 0).toInt + 1))} else { Map() }
          case None => Map()
        }
      }
      catch {
        case e: Throwable => Map()
      }
      map ++ Map("sum" -> (x.getOrElse("sum", 0).toInt + 1)) ++ taken
    }
    else {
      x
    }
  }
}
