import collection.mutable.Stack
import org.scalatest._
import elementary.glue.PythonInterop

class PythonSpec extends FlatSpec with Matchers {
  "The Test environment" should "create a simple python script" in {
    import java.io._
    val pw = new PrintWriter(new File("test.py" ))
    pw.println("def test1():")
    pw.println("  return 5")
    pw.println("def test2(input):")
    pw.println("  return input+5")
    pw.close
  }

  "A Python Interpreter" should "should be creatable" in {
    val python = PythonInterop.create

    python should not be None

    python match {
      case Some(interop) => interop.close
      case None => ()
    }
  }

  it should "run a python script" in {
    val python = PythonInterop.create

    python should not be None

    python match {
      case Some(interop) => {
        interop.loadScript("test.py") should be (true)

        interop.loadScript("not here.py") should be (false)

        val data1 = interop.callFunction("test1")
        data1 should be (Some(5))

        val data2 = interop.callFunction("test2", 5.asInstanceOf[AnyRef])
        data2 should be (Some(10))

        val data3 = interop.callFunction("not_existing")
        data3 should be (None)

        interop.close
      }
      case None => ()
    }
  }

  it should "run evaluate and run python scripts" in {
    val python = PythonInterop.create

    python should not be None

    python match {
      case Some(interop) => {
        interop.evaluate("def test():\n  return 9") should be (true)

        interop.evaluate("sdfadda") should be (false)

        val data = interop.callFunction("test")
        data should be (Some(9))

        interop.close
      }
      case None => ()
    }
  }

  "The Test environment" should "delete the script" in {
    import java.io._
    val file = new File("test.py" )
    file.delete()
  }
}
