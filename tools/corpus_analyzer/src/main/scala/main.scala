package elementary.tools.corpusanalyzer

import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

// Holds the configuration for this analyzer
case class AnalysisConfiguration(output: String)

// Defines an analysis tool for the corpus data
object CorpusAnalyzer extends App  {
  import scala.concurrent.ExecutionContext.Implicits.global

  println("Lecture Corpus Analysis Tool")
  println("============================\n")

  // load the configuration
  val configOpt = loadConfig(args)

  configOpt match {
    case Some(config) => {
      println("Starting Analysis with following specs:")
      println(config)

      println("\nStarting Analysis...")

      val flow = new AnalysisFlow(config)
      val result = flow.start()
      // hook on the future to create results
      result onComplete {
        case Success(res) =>
          println("Analysis completed!\n")
          println("Results:")
          println("========")
          println(s"Total Documents: \t ${res.count}")
          println(s"Number of Words: \t ${res.wordCount}")
          println(s"Unique Words: \t\t ${res.words}")
          println(s"Unique Topics: \t\t ${res.topics}")
          println(s"Unique Lectures: \t ${res.lectures}")
          println(s"Avg Sentence Length:\t ${res.sentenceLength}")
        case Failure(e) =>
          println("Analysis failed!\n")
          println("Exception: ")
          println(e)
      }
      // second hook on future to shutdown the actor system
      result onComplete {
        case _ => flow.close()
      }
    }
    case None =>
  }

  // search args for parameters / load remaining from typesafe config
  def loadConfig(args: Array[String]): Option[AnalysisConfiguration] = {
    // load the config file
    val config = ConfigFactory.load()

    // convert array to map
    @annotation.tailrec
    def loop(argsLs: List[String], result: Map[String, String] = Map()): Map[String, String] = {
      argsLs match {
        case name :: value :: tail if name.startsWith("-") =>
          val res = result + (name.substring(1).toLowerCase -> value)
          loop(tail, res)
        case name :: value :: tail => loop(tail, result)
        case name :: tail if name.toLowerCase == "--help" =>
          printHelp()
          result + ("help" -> "")
        case _ => result
      }
    }
    val argMap = loop(args.toList)

    if (argMap.contains("help")) None
    else {
      // go through all possible arguments
      // load the file information
      val output = argMap.get("output").getOrElse(config.getString("elementary.tools.corpusanalyzer.output"))
      val timestamp = argMap.get("timestamp").map(x => x.toBoolean).getOrElse(config.getBoolean("elementary.tools.corpusanalyzer.timestamp"))
      val file = output + "-" + (if (timestamp) System.currentTimeMillis.toString else "")
      Some(AnalysisConfiguration(file))
    }
  }

  // displays all possible command to edit the config
  def printHelp() = {
    println("Possible Commands:")
    println("==================")
    println("--help \t\t\t Shows all commands")
    println("-output [String] \t The basic name of the output csv")
    println("-timestamp [Bool] \t Defines if a time suffix should be added to the file")
  }
}
