package edu.illinois.osl.uigc.engines.crgc

import edu.illinois.osl.uigc.interfaces.Message

sealed trait GCMessage[+T] extends Message

final case class AppMsg[+T](
  payload: T, refs: Iterable[Refob[Nothing]]
) extends GCMessage[T] {
  var windowID: Int = -1
    // This field is set by the egress if the message gets sent to another node.
}

case object StopMsg extends GCMessage[Any] {
  override def refs: Iterable[Refob[Nothing]] = Nil
}

case object WaveMsg extends GCMessage[Any] {
  override def refs: Iterable[Refob[Nothing]] = Nil
}
