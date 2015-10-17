package elementary.tools.corpusanalyzer.models

import slick.driver.MySQLDriver.api._
import slick.lifted.{ProvenShape, ForeignKeyQuery}

object Tables {
  case class GeneralKV(key: String, count: Int)

  abstract class BaseTable(tag: Tag, name: String) extends Table[GeneralKV](tag, name) {
    def key: Rep[String] = column[String]("key", O.PrimaryKey, O.SqlType("varchar(250)"))
    def count: Rep[Int] = column[Int]("count")
    def * = (key, count) <> (GeneralKV.tupled, GeneralKV.unapply)
  }

  class CoocWords(tag: Tag) extends BaseTable(tag, "CoocWords")
  val coocwords = TableQuery[CoocWords]
  class CoocSents(tag: Tag) extends BaseTable(tag, "CoocSentences")
  val coocsents = TableQuery[CoocSents]

  class NERBreeze(tag: Tag) extends BaseTable(tag, "NERBreeze")
  val ner_breeze = TableQuery[NERBreeze]

  class NERDbp(tag: Tag) extends BaseTable(tag, "NERDbp")
  val ner_dbp = TableQuery[NERDbp]

  class POSBreeze(tag: Tag) extends BaseTable(tag, "POSBreeze")
  val pos_breeze = TableQuery[POSBreeze]

  class Words(tag: Tag) extends BaseTable(tag, "Words")
  val words = TableQuery[Words]

  class Lectures(tag: Tag) extends BaseTable(tag, "Departments")
  val lectures = TableQuery[Lectures]

  class Topics(tag: Tag) extends BaseTable(tag, "Modules")
  val topics = TableQuery[Topics]

  class Sentences(tag: Tag) extends BaseTable(tag, "Sentences")
  val sentences = TableQuery[Sentences]

  class General(tag: Tag) extends BaseTable(tag, "General")
  val general = TableQuery[General]
}
