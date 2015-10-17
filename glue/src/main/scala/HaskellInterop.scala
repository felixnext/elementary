package elementary.glue

import scala.sys.process._

/**
* Defines functions to interact with Haskell Code
* @param service the path of the haskell programm that should be used
**/
class HaskellInterop(service: String) extends Interop {
  //init: start the haskell tool in seperated process
  val process = Process(s"ghci $service")
  val handle = process.run

  //TODO: create remote process call handle

  //TODO: call haskell functions using Remote Process Calls (RPC)
  //idea: http://java.dzone.com/articles/xml-rpc-using-scala

  def close() = {
    handle.destroy
  }
}

//! Companion object for the haskell interop class
/*object HaskellInterop {
  // TODO
}*/
