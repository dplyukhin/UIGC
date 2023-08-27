package edu.illinois.osl.uigc.engines.drl

import edu.illinois.osl.uigc.interfaces.{Message, NoRefs}
import edu.illinois.osl.uigc.interfaces.Pretty

sealed trait GCMessage[+T] extends Message with Pretty

final case class AppMsg[+T](
  payload: T, token: Option[Token], refs: Iterable[Refob[Nothing]]
) extends GCMessage[T] {
  def pretty: String = token match {
    case None => s"AppMsg(null, $payload, ${refs.toList.pretty})"
    case Some(t) => s"AppMsg(${t.pretty}, $payload, ${refs.toList.pretty})"
  }
}

final case class ReleaseMsg[+T](
  releasing: Iterable[Refob[Nothing]],
  created: Iterable[Refob[Nothing]],
) extends GCMessage[T] with NoRefs {
  def pretty: String = s"ReleaseMsg(releasing: ${releasing.toList.pretty}, created: ${created.toList.pretty})"
}

// /** A message asking its recipient to take a snapshot. */
// case object TakeSnapshot extends GCMessage[Nothing]

/**
 * A message sent by an actor to itself to check whether it's ready to
 * terminate.  
 */
case object SelfCheck extends GCMessage[Nothing] with NoRefs {
  def pretty: String = "SelfCheck"
}

/** 
 * A message sent by the garbage collector, indicating that this actor is
 * garbage.
 */
case object Kill extends GCMessage[Nothing] with NoRefs {
  def pretty: String = "Kill"
}
