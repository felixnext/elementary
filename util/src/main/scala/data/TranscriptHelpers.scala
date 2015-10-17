package elementary.util.data

sealed trait TranscriptDB
final case object Snippets extends TranscriptDB
final case object Transcripts extends TranscriptDB

sealed class ClueRef(id: String, probability: Double, start: Long, length: Long, surfaceForm: Option[String])
final case class TranscriptRef(id: String, probability: Double, start: Long, length: Long, surfaceForm: Option[String])
  extends ClueRef(id, probability, start, length, surfaceForm)
final case class EntityRef(uri: String, probability: Double, start: Long, length: Long, surfaceForm: String, types: Seq[String])
  extends ClueRef(uri, probability, start, length, Some(surfaceForm))
final case class PaperRef(id: String, probability: Double, start: Long, length: Long, surfaceForm: Option[String])
  extends ClueRef(id, probability, start, length, surfaceForm)
final case class TranscriptVector(_id: String, id: String, vector: Array[Double])
