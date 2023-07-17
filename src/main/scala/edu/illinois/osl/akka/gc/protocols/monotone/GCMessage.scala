package edu.illinois.osl.akka.gc.protocols.monotone

import edu.illinois.osl.akka.gc.interfaces.{Message, Pretty}

sealed trait GCMessage[+T] extends Message with Pretty

final case class AppMsg[+T](
  payload: T, refs: Iterable[Refob[Nothing]]
) extends GCMessage[T] {
  def pretty: String = s"AppMsg($payload, ${refs.toList.pretty})"
}

case class StopMsg() extends GCMessage[Any] {
  override def pretty: String = "STOP"
  override def refs: Iterable[Refob[Nothing]] = Nil
}