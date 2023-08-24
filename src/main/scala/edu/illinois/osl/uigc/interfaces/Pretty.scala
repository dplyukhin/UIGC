package edu.illinois.osl.uigc.interfaces

import scala.collection

object Pretty {

  class PrettySeq[T <: Pretty](seq: collection.Seq[T]) extends Pretty {
    def pretty: String = "{ " + seq.map(_.pretty).mkString(", ") + " }"
  }

  implicit def prettifySeq[T <: Pretty](seq: collection.Seq[T]): Pretty = new PrettySeq(seq)

  class PrettySet[T <: Pretty](set: collection.Set[T]) extends Pretty {
    def pretty: String = "{ " + set.map(_.pretty).mkString(", ") + " }"
  }

  implicit def prettifySeq[T <: Pretty](set: collection.Set[T]): Pretty = new PrettySet(set)

  class PrettyMap[K <: Pretty, V](map: collection.Map[K,V]) extends Pretty {
    def pretty: String = "{ " + map.map{ case (k,v) => s"${k.pretty}: ${v.toString()}" }.toList.mkString(", ") + " }"
  }

  implicit def prettifyMap[K <: Pretty, V](map: collection.Map[K,V]): Pretty = new PrettyMap(map)

  class PrettyInt(n: Int) extends Pretty {
    def pretty: String = n.toString()
  }

  implicit def prettifyInt(n: Int): Pretty = new PrettyInt(n)
}

trait Pretty {
  def pretty: String
}
