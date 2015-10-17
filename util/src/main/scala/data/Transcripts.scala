package elementary.util.data

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.SearchType
import com.typesafe.config.ConfigFactory
import org.elasticsearch.search.SearchHit
import org.elasticsearch.action.get.GetResponse
import scala.concurrent.Future
import scala.util.Try
import scala.collection.JavaConversions._
import scala.collection.mutable

final case class Transcript(id: String, docId: String, corpus: String, lectype: String, module: String, department: String, title: String,
   text: String, clues: List[ClueRef], offset: Int = 0)

object Transcript extends ElasticSearchFacade {
  import scala.concurrent.ExecutionContext.Implicits.global
  // retrieve the name of the database
  def dbName(db: TranscriptDB) = db match { case Transcripts => "transcripts" case Snippets => "snippets" }

  // load the name of the distributional semantic model that should be used
  val dsm = {
    val config = ConfigFactory.load()
    config.getString("elementary.parameter.general.ds-model")
  }

  // returns all documents in the db
  def getAll(db: TranscriptDB = Transcripts): Future[List[Transcript]] = {
    countAll(db).flatMap(count => {
      val query = search in dbName(db) -> "doc" query matchall size count.toInt
      val res = resultMap(executeQuery(query))(parse)
      res.map( ls => ls.filterNot(_ == None).map(_.get._1) )
    })
  }

  // counts all docs in the db
  def countAll(db: TranscriptDB = Transcripts): Future[Long] = {
    val query = count from dbName(db) / "doc"
    executeQuery(query).map(_.getCount())
  }

  // gets a part of all documents (starting at 'start' and selecting 'offset' docs)
  def getAllPage(start: Int, offset: Int, db: TranscriptDB = Transcripts): Future[List[Transcript]] = {
    val query = search in dbName(db) -> "doc" query matchall searchType SearchType.Scan start start limit offset
    val res = resultMap(executeQuery(query))(parse)
    res.map( ls => ls.filterNot(_ == None).map(_.get._1) )
  }

  // searches for multiple terms
  def findTerms(terms: List[String], db: TranscriptDB = Transcripts): Future[List[(Transcript, Double)]] = {
    val query = search in dbName(db) -> "doc" query {
      bool {
        should {
          terms.map(term => matchQuery("text", term)).toSeq
        }
      }
    } limit 100
    val res = resultMap(executeQuery(query))(parse)
    res.map( ls => ls.filterNot(_ == None).map(_.get) )
  }

  // search for sepcfic entities
  def findEntities(entities: List[String], db: TranscriptDB = Transcripts): Future[List[(Transcript, Double)]] = {
    val query = search in dbName(db) -> "doc" query {
      bool {
        should {
          entities.map(ent => matchQuery("clues.entity", ent)).toSeq
        }
      }
    }
    val res = resultMap(executeQuery(query))(parse)
    res.map( ls => ls.filterNot(_ == None).map(_.get) )
  }

  // find documents based on the specified topics
  def findTopics(topics: List[String], db: TranscriptDB = Transcripts): Future[List[(Transcript, Double)]] = {
    val query = search in dbName(db) -> "doc" query {
      bool {
        should {
          topics.map(topic => matchQuery("topics.name", topic)).toSeq
        }
      }
    }
    val res = resultMap(executeQuery(query))(parse)
    res.map( ls => ls.filterNot(_ == None).map(_.get) )
  }

  // find documents based on all criteria
  def find(terms: List[String], entities: List[String] = List(), topics: List[String] = List(),
    db: TranscriptDB = Transcripts): Future[List[(Transcript, Double)]] = {
    val query = search in dbName(db) -> "doc" query {
      bool {
        should {
          terms.map(term => matchQuery("text", term)).toSeq ++
          entities.map(ent => matchQuery("entities.entity", ent)).toSeq ++
          topics.map(topic => matchQuery("topics.name", topic)).toSeq
        }
      }
    }
    val res = resultMap(executeQuery(query))(parse)
    res.map( ls => ls.filterNot(_ == None).map(_.get) )
  }

  // searchs for a document with the given id in the id-field
  def findId(id: String, db: TranscriptDB = Transcripts): Future[List[(Transcript, Double)]] = {
    val query = search in dbName(db) / "doc" query { termQuery("id", id) }
    val res = resultMap(executeQuery(query))(parse)
    res.map( ls => ls.filterNot(_ == None).map(_.get) )
  }

  // retrieves the document with the given id as _id
  def getId(docid: String, db: TranscriptDB = Transcripts): Future[Option[Transcript]] = {
    val query = get id docid from dbName(db) / "doc"
    executeQuery(query).map(parse)
  }

  // loads all available transcripts from the database
  def vectors(db: TranscriptDB = Transcripts): Future[List[TranscriptVector]] = {
    val query = search in dbName(db) / "doc" query matchall
    val res = resultMap(executeQuery(query))(parseVector)
    res.map( ls => ls.filterNot(_ == None).map(_.get) )
  }

  // loads all available vectors for a single document
  def vectorDoc(docid: String, db: TranscriptDB = Transcripts): Future[List[TranscriptVector]] = {
    val query = search in dbName(db) / "doc" query { termQuery("id", docid) }
    val res = resultMap(executeQuery(query))(parseVector)
    res.map( ls => ls.filterNot(_ == None).map(_.get) )
  }

  // get the vector data from a document
  def parseVector(hit: SearchHit): Option[TranscriptVector] = {
    import scala.collection.mutable.Map

    val map = hit.sourceAsMap()
    lazy val vectorOpt = Try( map.get("vectors").asInstanceOf[java.util.HashMap[String, java.util.ArrayList[Object]]] ).toOption
    lazy val vector = vectorOpt.flatMap(data => {
      val map = mapAsScalaMap(data)
      map.get(dsm) match {
        case Some(optdata) => optdata match {
          case vecjls: java.util.ArrayList[Object] =>
            Some(vecjls.map(value => Try(value.toString.toDouble).getOrElse(0.0)).toArray)
          case _ => None
        }
        case None => None
      }
    })
    Try( TranscriptVector(hit.getId(), map.get("id").toString, vector.get) ).toOption
  }

  def parse(hit: GetResponse): Option[Transcript] = {
    if (hit.isExists()) {
      val map = hit.getSourceAsMap()
      lazy val clueOpt = Try( map.get("clues").asInstanceOf[java.util.ArrayList[java.util.HashMap[String, Object]]] ).toOption
      lazy val clues = clueOpt.map(data => parseHashMap(data)(smap => smap("type").toString match {
        case "entity" => EntityRef(smap("id").toString, smap("probability").toString.toDouble, smap("start").toString.toInt,
          smap("length").toString.toInt, smap("surfaceForm").toString, Seq())
      }))
      lazy val trans = Transcript(map.get("id").toString, hit.getId(), map.get("corpus").toString, map.get("type").toString,
        map.getOrElse("module", "").toString, map.get("department").toString,
        map.getOrElse("title", "").toString, map.get("text").toString, clues.getOrElse(List()), map.getOrElse("offset", "0").toString.toInt)
      Try(trans).toOption
    }
    else None
  }

  // parse a SearchHit into a tuple of transcript and scoring
  def parse(hit: SearchHit): Option[(Transcript, Double)] = {
    import scala.collection.mutable.Map

    // as the cast functions might throw exceptions, they are lazy (will be executed in the Try block)
    val map = hit.sourceAsMap()
    lazy val clueOpt = Try( map.get("clues").asInstanceOf[java.util.ArrayList[java.util.HashMap[String, Object]]] ).toOption
    lazy val clues = clueOpt.map(data => parseHashMap(data)(smap => smap("type").toString match {
      case "entity" => EntityRef(smap("id").toString, smap("probability").toString.toDouble, smap("start").toString.toInt,
        smap("length").toString.toInt, smap("surfaceForm").toString, Seq())
    }))

    // construct the final transcript and return it as a tuple with score
    lazy val trans = Transcript(map.get("id").toString, hit.getId(), map.get("corpus").toString, map.get("type").toString,
      map.getOrElse("module", "").toString, map.get("department").toString,
      map.getOrElse("title", "").toString, map.get("text").toString, clues.getOrElse(List()), map.getOrElse("offset", "0").toString.toInt)
    Try( (trans, hit.getScore().toDouble) ).toOption
  }

  private def parseHashMap[T](data: java.util.ArrayList[java.util.HashMap[String, Object]])(f: mutable.Map[String, Object] => T): List[T] = {
    val ls: Seq[java.util.HashMap[String, Object]] = data
    val res = ls.toList.map( map => {
      val smap = mapAsScalaMap(map)
      f(smap)
    })
    res
  }
}
