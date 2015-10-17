// Original Code: Copyright 2013 trananh - Licensed under the Apache License, Version 2.0 (the "License");
package elementary.util.machinelearning

import com.typesafe.config.Config
import scala.Array
import scala.collection.mutable

object VectorMath {
  // sums two arrays (aka vectors)
  def sumArray (m: Array[Double], n: Array[Double]): Array[Double] = {
    @annotation.tailrec
    def loop(i: Int, res: Array[Double]): Array[Double] = {
      if (i<res.length) {
        res(i) = m(i) + n(i)
        loop(i+1, res)
      }
      else res
    }
    loop(0, new Array[Double]( math.min(m.length, n.length) ))
  }

  // Divides each value in the area by the given one
  def divArray (m: Array[Double], divisor: Double) : Array[Double] = {
    if(divisor > 0) m.map(_ / divisor)
    else            m
  }

  def avgArray(ls: List[Array[Double]], dim: Int): Option[Array[Double]] = {
    def checkArray(arr: Array[Double], count: Int): Double = if(arr.size > count) arr(count) else 0.0
    @annotation.tailrec
    def loop(i: Int, res: Array[Double]): Array[Double] = {
      if (i<dim) {
        res(i) = ls.foldLeft(0.0)((o, n) => o + checkArray(n, i))
        loop(i+1, res)
      }
      else res
    }
    if(ls.size == 0) None
    else             Some(divArray( loop(0, new Array[Double]( dim )), ls.size))
  }

  // Compute the Euclidean distance between two vectors.
  def euclidean(vec1: Array[Double], vec2: Array[Double]): Option[Double] = {
    if (vec1.length != vec2.length) None
    else {
      @annotation.tailrec
      def loop(i: Int = 0, sum: Double = 0.0): Double = {
        if(i < vec1.length) loop(i+1, sum + math.pow(vec1(i) - vec2(i), 2))
        else sum
      }
      Some(math.sqrt(loop(0)))
    }
  }

  // Compute the cosine similarity score between two vectors.
  def cosine(vec1: Array[Double], vec2: Array[Double]): Option[Double] = {
    if (vec1.length != vec2.length) None
    else {
      @annotation.tailrec
      def loop(i: Int, dot: Double = 0.0, sum1: Double = 0.0, sum2: Double = 0.0): (Double, Double, Double) = {
        if (i < vec1.length) loop(i+1, dot + (vec1(i) * vec2(i)), sum1 + (vec1(i) * vec1(i)), sum2 + (vec2(i) * vec2(i)))
        else (dot, sum1, sum2)
      }
      val (dot, sum1, sum2) = loop(0)
      Some(dot / (math.sqrt(sum1) * math.sqrt(sum2)))
    }
  }

  // Calculate the top candidates for the nearest neighbours
  def nearestNeighbors(vector: Array[Double], items: List[(String, Array[Double])], N: Int): List[(String, Double)] = {
    val top = new mutable.PriorityQueue[(String, Double)]()(Ordering.by(-_._2))
    // calculate the cosine to each vector
    val lsp = items.toList.par.map(t => (t._1, VectorMath.cosine(vector, t._2) match { case None => -1.0 case Some(value) => value}))
    val ls: List[(String, Double)] = lsp.toList
    ls.foreach(entry => {
      if (top.size < N || top.head._2 < entry._2) {
        top.enqueue(entry)
        // if to long pop the element with the lowest score
        if (top.length > N) {
          top.dequeue()
        }
      }
    })
    // return the results
    if (top.length < N) List()
    else top.toList.sortWith(_._2 > _._2)
  }

  // Compute the magnitude of the vector.
  def magnitude(vec: Array[Double]): Double = math.sqrt(vec.foldLeft(0.0){(sum, x) => sum + (x * x)})

  // Normalize the vector.
  def normalize(vec: Array[Double]): Array[Double] = {
    val mag = magnitude(vec)
    vec.map(_ / mag)
  }
}
