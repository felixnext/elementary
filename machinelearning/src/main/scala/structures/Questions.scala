package elementary.util.machinelearning.structures

sealed trait AnswerType
sealed trait FactoidAT extends AnswerType
final case object PersonFAT   extends FactoidAT
final case object LocationFAT extends FactoidAT
final case object DateFAT     extends FactoidAT

sealed trait NonFactoidAT extends AnswerType
final case class  EntityNFAT(entity: String) extends NonFactoidAT
final case object UnknownNFAT                extends NonFactoidAT

sealed trait QuestionType
final case object WhyQT   extends QuestionType
final case object HowQT   extends QuestionType
final case object WhoQT   extends QuestionType
final case object WhereQT extends QuestionType
final case object WhatQT  extends QuestionType
final case object WhenQT  extends QuestionType
final case object WhichQT extends QuestionType
final case object OtherQT extends QuestionType
// note: what about questions like: "can we ...?"
