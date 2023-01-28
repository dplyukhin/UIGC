package edu.illinois.osl.akka.gc.interfaces

import scala.collection

object Pretty {

  class PrettyList(list: List[Pretty]) extends Pretty {
    def pretty: String = 
      if (list.nonEmpty) 
        list.map(_.pretty).mkString(", ")
      else
        "(nothing)"
  }

  implicit def prettifyList(list: List[Pretty]): Pretty = new PrettyList(list)

  class PrettySet[T <: Pretty](set: collection.Set[T]) extends Pretty {
    def pretty: String = "{ " + set.map(_.pretty).mkString(", ") + " }"
  }

  implicit def prettifySet[T <: Pretty](set: collection.Set[T]): Pretty = new PrettySet(set)

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