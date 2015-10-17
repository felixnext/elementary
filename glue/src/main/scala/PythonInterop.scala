package elementary.glue

import java.io.File
import jep._
import scala.util.{Try, Success, Failure}

/**
* Defines a class to call python scripts
* NOTE: as this depeneds on java library it will have global state!
**/
class PythonInterop(jep: Jep) extends Interop {
  /**
  * Loads the python script
  * @param script the path of the script that should be executed
  * @return true if the script was loaded, otherwise false
  **/
  def loadScript(script : String): Either[Throwable, Boolean] = {
    //safty: check if script file is valid
    val file = new File(script)
    if (!file.exists()) Right(false)
    else {
      // load the script
      Try(jep.runScript(script)) match {
        case Success(_) => Right(true)
        case Failure(e) => Left(e)
      }
    }
  }

  /**
  * Evaluates the given python code
  * @return true if the code was evaluated correctly, otherwise false
  **/
  def evaluate(code: String): Boolean = {
    Try(jep.eval(code)) match {
      case Success(_) => true
      case Failure(e) => false
    }
  }

  /**
  * Evaluates a Method
  */
  def callMethod(fct: String): Either[Throwable, Object] = {
    Try(jep.getValue(fct)) match {
      case Success(result) => Right(result)
      case Failure(e)      => Left(e)
    }
  }

  /**
  * Calls a functions on a previously loaded script
  * Note: Output can be casted with '.asInstanceOf[Class]'
  * @param fct the name of the function that should be called
  * @param params the list of parameters that should be passed to the function (have to be in the correct order)
  * @return Object from the function (miight be unit)
  **/
  def callFunction(fct: String, params: Any*): Either[Throwable, Object] = {
    Try(jep.invoke(fct, params.toArray.map(_.asInstanceOf[AnyRef]):_*)) match {
      case Success(result) => Right(result)
      case Failure(e)      => Left(e)
    }
  }

  //! Destructs all elements
  def close() = {
    jep.close()
  }
}

//! Defines the companion object for the python interop class
object PythonInterop {
  //! Create a new Python Interop entity
  def create: Either[Throwable, PythonInterop] = {
    // create the class blocking jep
    Try(new Jep(false, null, null, new AntiClassList())) match {
      case Success(jep) => Right(new PythonInterop(jep))
      case Failure(e)   => Left(e)
    }
  }

  //! Creates a Class Enquirer to avoid java packages
  class AntiClassList extends ClassEnquirer {
    def contains(name: String): Boolean = false
    def supportsPackageImport(): Boolean = false
    def getClassNames(pkgName: String): Array[String] = Array.empty
  }
}
