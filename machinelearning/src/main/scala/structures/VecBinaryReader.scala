// Original Code: Copyright 2013 trananh - Licensed under the Apache License, Version 2.0 (the "License");
package elementary.util.machinelearning.structures

import java.io._
import scala.Array
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.{Try, Success, Failure}

// Binary file reader (used to read the model files)
class VecBinaryReader(val file: File) {

  // Overloaded constructor
  def this(filename: String) = this(new File(filename))

  // ASCII values for common delimiter characters
  private val SPACE = 32
  private val LF = 10

  // Open input streams
  private val fis = new FileInputStream(file)
  private val bis = new BufferedInputStream(fis)
  private val dis = new DataInputStream(bis)

  // Close the stream.
  def close() = { Try(dis.close()); Try(bis.close()); Try(fis.close()) }

  // Read the next byte.
  def read(): Either[Throwable, Byte] = Try(dis.readByte()) match { case Success(byte) => Right(byte); case Failure(e) => Left(e) }

  // Read the next token as a string, using the provided delimiters as breaking points.
  def readToken(delimiters: Set[Int] = Set(SPACE, LF)): Either[Throwable, String] = {
    // iterate through all bytes
    @annotation.tailrec
    def loop(byte: Byte, results: List[Byte] = List()): Either[Throwable, List[Byte]] = {
      if(!delimiters.contains(byte))
        Try(dis.readByte()) match {
          case Success(nbyte) => loop(nbyte, results :+ byte)
          case Failure(e) => Left(e)
        }
      else Right(results)
    }
    // check the result
    loop(dis.readByte()) match {
      case Right(ls) =>
        val sb = new StringBuilder()
        Right(sb.append(new String(ls.toArray)).toString())
      case Left(e) => Left(e)
    }
  }

  // Read next 4 bytes as a floating-point number.
  def readFloat(): Either[Throwable, Float] = {
    // We need to reverse the byte order here due to endian-compatibility.
    Try(dis.readInt()) match {
      case Success(i) => Right(java.lang.Float.intBitsToFloat(java.lang.Integer.reverseBytes(i)))
      case Failure(e) => Left(e)
    }
  }

}
