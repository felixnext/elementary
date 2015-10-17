package elementary.tools.baseimporter

import com.sksamuel.elastic4s.{ElasticClient, ElasticsearchClientUri}
import com.sksamuel.elastic4s.ElasticDsl._
import org.elasticsearch.common.settings.ImmutableSettings
import java.io.File
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

object BaseImport {
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.language.postfixOps

  val settings = ImmutableSettings.settingsBuilder().put("cluster.name", "elementary").build()
  implicit val client = ElasticClient.remote(
    settings,
    ElasticsearchClientUri("elasticsearch://is62.idb.cs.tu-bs.de:9300")
  )

  def main(args: Array[String]) = {
    println("BASE Indexing Tool...")
    println("use '-data [base|aiv|wiki|transcripts|snippets]' to specify the data to operate on.")
    println("use '-mode [index|preprocess|delete_pre|delete_snip|delete|vectorize|unvectorize]' to specify operation mode.")
    println("  note: preprocess, delete_pre and delete_snip only available for [base|aiv]")
    println("  note: vectorize and delete only available for [transcripts|snippets]")
    println("  note: delete for [base|aiv] only deletes the base index/not the preprocessing data")
    println("use '-dir <path>' to specify the path of the Corpus (for index)")

    // parse arguments
    @annotation.tailrec
    def loop(argsLs: List[String], result: Map[String, String] = Map()): Map[String, String] = {
      argsLs match {
        case name :: value :: tail if name.startsWith("-") =>
          val res = result + (name.substring(1).toLowerCase -> value)
          loop(tail, res)
        case name :: value :: tail => loop(tail, result)
        case _ => result
      }
    }
    val argMap = loop(args.toList)
    val data = argMap.getOrElse("data", "").toLowerCase
    val mode = argMap.getOrElse("mode", "extract").toLowerCase
    val path = argMap.getOrElse("dir", "./")

    if(!(new java.io.File(path)).exists()) println(s"ERROR: The file '$path' does not exist!")
    else (data, mode) match {
      case ("base", "index") => println("Indexing BASE *.xml files...")
        val xmlfiles = getListOfFiles(path).filter(_.getName.endsWith("xml"))
        println(s"Found ${xmlfiles.size} docs...")
        (xmlfiles zip xmlfiles.indices).foreach(t => BaseProcessor.indexDocs(t._1, t._2))
      case ("aiv", "index") => println("Indexing AIV *.json files...")
        val xmlfiles = getListOfFiles(path).filter(_.getName.endsWith("json"))
        println(s"Found ${xmlfiles.size} docs...")
        xmlfiles.foreach(AIVProcessor.indexDocs)
      case ("wiki", "index") => println("Indexing Wikipedia *.xml files...")
        val xmlfiles = getListOfFiles(path).filter(_.getName.endsWith("xml"))
        println(s"Found ${xmlfiles.size} docs...")
        xmlfiles.foreach(WikiProcessor.indexDocs)
      case ("base", "preprocess") => println("Preprocessing BASE documents...")
        BaseProcessor.updateDocs(false)
      case ("aiv", "preprocess") => println("Preprocessing AIV documents...")
        AIVProcessor.updateDocs(false)
      case ("transcripts", "vectorize") => println("Calculate vectors for Transcripts...")
        W2VProcessor.vectorize("transcripts", path)
      case ("snippets", "vectorize") => println("Calculate vectors for Snippets...")
        W2VProcessor.vectorize("snippets", path)
      case ("transcripts", "unvectorize") => println("Delete vectors for Transcripts...")
        W2VProcessor.unvectorize("transcripts")
      case ("snippets", "unvectorize") => println("Delete vectors for Snippets...")
        W2VProcessor.unvectorize("snippets")
      case ("transcripts", "clue") => println("Calculate clues for Transcripts...")
        W2VProcessor.clue("transcripts")
      case ("snippets", "clue") => println("Calculate clues for Snippets...")
        W2VProcessor.clue("snippets")
      case ("transcripts", "unclue") => println("Delete clues for Transcripts...")
        W2VProcessor.clue("transcripts", true)
      case ("snippets", "unclue") => println("Delete clues for Snippets...")
        W2VProcessor.clue("snippets", true)
      case ("transcripts", "delete") => println("Deleting Transcripts...")
        W2VProcessor.deleteTS
      case ("snippets", "delete") => println("Deleting Snippets...")
        W2VProcessor.deleteSNIP
      case ("base", "snippet") => println("Preprocessing BASE documents into snippets...")
        BaseProcessor.updateDocs(true)
      case ("aiv", "snippet") => println("Preprocessing AIV document into snippetss...")
        AIVProcessor.updateDocs(true)
      case ("base", "delete_pre") => println("Delete preprocessed BASE documents...")
        BaseProcessor.deletePre("transcripts")
      case ("aiv", "delete_pre") => println("Delete preprocessed AIV documents...")
        AIVProcessor.deletePre("transcripts")
      case ("base", "delete_snip") => println("Delete preprocessed BASE snippets...")
        BaseProcessor.deletePre("snippets")
      case ("aiv", "delete_snip") => println("Delete preprocessed AIV snippets...")
        AIVProcessor.deletePre("snippets")
      case ("base", "delete") => println("Reset BASE Index...")
        BaseProcessor.deleteDocs
      case ("aiv", "delete") => println("Reset AIV Index...")
        AIVProcessor.deleteDocs
      case ("wiki", "delete") => println("Reset Wikipedia Index...")
        WikiProcessor.deleteDocs
      case ("w2v", "test") => println("Entering word2vec test...")
        w2vTest(path)
      case ("d2v", "test") => println("Testing Doc2Vec ...")
        d2vTest(path)
      case ("db", "test") => println("Testing Transcripts...")
        tscriptTest()
      case _ => println("ERROR: Unkown operation")
    }
    println(s"$data/$mode completed!")
    client.close()
    println("closed connection")
  }

  def tscriptTest() = {
    import scala.concurrent.duration.Duration
    import elementary.util.data._

    val count = Await.result(Transcript.countAll(Snippets), Duration.Inf)
    println(s"Doc count: $count")
    val docs = Await.result(Transcript.getAll(Snippets), Duration.Inf)
    println(s"Docs retrieved: ${docs.size}")
  }

  def d2vTest(model: String) = {
    import elementary.util.machinelearning.{Word2Vec, Doc2VecPy}

    Doc2VecPy.create(model) match {
      case Right(d2v) =>
        println("Loaded model!")
        val contains = d2v.contains("queen")
        println(s"Contains word 'queen': $contains")
        val vector = d2v.vector("queen")
        println(s"Vector for word 'queen': $vector")
        val paragraph =
          d2v.paragraph("In 2004, Obama received national attention during his campaign to represent Illinois in the United States Senate")
        println(s"Paragraph for section about obama: $paragraph")
        val sim = d2v.cosine("queen", "king")
        println(s"similarity for word 'queen' and 'king': $sim")
        val ana = d2v.analogy("king", "man", "woman")
        println(s"Analogy for 'king' - 'man' + 'woman': $ana")
      case Left(e) => println(s"Failed to load model: $e")
    }
  }

  def w2vTest(model: String) = {
    import elementary.util.machinelearning.{Word2Vec, Word2VecLocal}

    def w2vInteract(w2v: Word2Vec): Unit = {
      println("Options:")
      println("  analogy <word1> <word2> <word3>")
      println("  distance <word>")
      println("  exit")
      val input = scala.io.StdIn.readLine
      val args = input.split(" ")
      if (args.size > 0) {
        args(0) match {
          case "analogy" if args.size > 3 =>
            val time = System.currentTimeMillis
            val res = w2v.analogy(args(1), args(2), args(3))
            val dif = System.currentTimeMillis - time
            println(s"Calculated result in ${dif.toFloat / 1000.0f} seconds")
            println("RESULTS:")
            println("---------")
            res.foreach(tpl => println(s"${tpl._1} (${tpl._2})"))
          case "distance" if args.size > 1 =>
            val time = System.currentTimeMillis
            val res = w2v.distance(List(args(1)))
            val dif = System.currentTimeMillis - time
            println(s"Calculated result in ${dif.toFloat / 1000.0f} seconds")
            println("RESULTS:")
            println("---------")
            res.foreach(tpl => println(s"${tpl._1} (${tpl._2})"))
          case "exit" => println("bye!")
          case _ => { println("Command not found!"); w2vInteract(w2v) }
        }
      }
      else println("no command found!"); w2vInteract(w2v)
    }
    println("Loading Model...")
    val tModel = System.currentTimeMillis
    val w2vModel = Word2VecLocal.load(model)
    val difModel = System.currentTimeMillis - tModel
    println(s"Loaded Model '$model' in ${difModel.toFloat / 1000.0f} seconds")
    w2vModel match {
      case Right(model) => w2vInteract(model)
      case Left(e) => println("ERROR: could not load model")
    }
  }
  //list all files in dir
  def getListOfFiles(dir: String) : List[File] = {
    val d = new File(dir)
    if(d.exists && d.isDirectory) {
      d.listFiles.filter(_.isFile).toList
    }
    else {
      List[File]()
    }
  }
}
