import collection.mutable.Stack
import org.scalatest._
import elementary.glue.PrologInterop

class PrologSpec extends FlatSpec with Matchers {
  "The Test environment" should "create a simple prolog script" in {
    import java.io._
    val pw = new PrintWriter(new File("test.pl" ))
    pw.println("lion(simba).")
    pw.println("animal(X) :- lion(X).")
    pw.println("friend('Jack', 'John').")
    pw.println("friend('John', 'Doe').")
    pw.println("friend(X,Z) :- friend(X,Y), friend(Y,Z).")
    pw.close
  }

  "A Prolog Interpreter" should "should be creatable" in {
    val prolog = PrologInterop.create

    prolog should not be None

    prolog match {
      case Some(interop) => interop.close
      case None => ()
    }
  }

  it should "load prolog script and query" in {
    val prolog = PrologInterop.create

    prolog should not be None

    prolog match {
      case Some(interop) => {
        interop.loadScript("test.pl") should be (true)

        val data1 = interop.query("friend('Jack', Person).")
        data1 should be (Some(List(Map("Person"->"John"), Map("Person"->"Doe"))))

        val data2 = interop.query("animal('simba')?")
        data2 should be (Some(List(Map("Result"->"True"))))

        interop.close
      }
      case None => ()
    }
  }

  it should "evaluate prolog script and query" in {
    val prolog = PrologInterop.create

    prolog should not be None

    prolog match {
      case Some(interop) => {
        interop.evaluate("lion('simba'). animal(X) :- lion(X).") should be (true)

        val data = interop.query("animal('simba')?")
        data should be (Some(List(Map("Result"->"True"))))

        interop.close
      }
      case None => ()
    }
  }

  "The Test environment" should "delete the script" in {
    import java.io._
    val file = new File("test.pl" )
    file.delete()
  }
}
