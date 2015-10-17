package elementary.tools.answerscrawler

import scala.concurrent.{Future, Await}

import scala.io.StdIn

object IOFlow extends TepkinFacade {
  // prints data to stdout
  def writeFile(q: Question) = {
    import java.io._
    if( q.category.contains("Science & Mathematics") && q.taken == Some(true) && q.types != List() ) {
      val fw = new FileWriter("test.csv", true)
      val taken = try {
        q.taken match {
          case Some(t) if (t) => 1
          case _ => 0
        }
      }
      catch {
        case e: Throwable => 0
      }
      val category = q.category.last
      val question: String = q.question + "#" +
          q.id + "#" +
          category + "#" +
          q.types.fold("")((x, y) => x + ", " + y) + "#" +
          taken + "\n"
      fw.write(question)
      fw.close()
    }
  }

  //question taken?
  def askYesNo(q: Question) : Question = {
    if(q.category.contains("Science & Mathematics")) {
      q.taken match {
        case Some(t) => {
          q
        }
        case None => {
          println(q.question + " (Y/N)")
          StdIn.readChar() match {
            case 'y' => { println("yes"); updateQuestion(q, true); q }
            case 'n' => { println("no"); updateQuestion(q, false); q }
            case _ => q
          }
        }
      }
    }
    else {
      q
    }
  }
}
