package edu.illinois.osl.akka.gc.protocols.monotone

import edu.illinois.osl.akka.gc.interfaces.{Message, Pretty}

sealed trait GCMessage[+T] extends Message with Pretty

final case class AppMsg[+T](
  payload: T, token: Option[Token], refs: Iterable[Refob[Nothing]]
) extends GCMessage[T] {
  def pretty: String = token match {
    case None => s"AppMsg(null, $payload, ${refs.toList.pretty})"
    case Some(t) => s"AppMsg(${t.pretty}, $payload, ${refs.toList.pretty})"
  }
}