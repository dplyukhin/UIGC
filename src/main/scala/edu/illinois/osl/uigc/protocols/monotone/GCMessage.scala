package edu.illinois.osl.uigc.protocols.monotone

import edu.illinois.osl.uigc.interfaces.{Message, Pretty}

sealed trait GCMessage[+T] extends Message with Pretty

final case class AppMsg[+T](
  payload: T, refs: Iterable[Refob[Nothing]]
) extends GCMessage[T] {
  var windowID: Int = -1
    // This field is set by the egress if the message gets sent to another node.
  def pretty: String = s"AppMsg($payload, ${refs.toList.pretty})"
}

case object StopMsg extends GCMessage[Any] {
  override def pretty: String = "STOP"
  override def refs: Iterable[Refob[Nothing]] = Nil
}

case object WaveMsg extends GCMessage[Any] {
  override def pretty: String = "WAVE"
  override def refs: Iterable[Refob[Nothing]] = Nil
}
