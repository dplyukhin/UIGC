package edu.illinois.osl.uigc.engines.drl

import edu.illinois.osl.uigc.interfaces.{Message, NoRefs}

sealed trait GCMessage[+T] extends Message

final case class AppMsg[+T](
  payload: T, token: Option[Token], refs: Iterable[Refob[Nothing]]
) extends GCMessage[T]

final case class ReleaseMsg[+T](
  releasing: Iterable[Refob[Nothing]],
  created: Iterable[Refob[Nothing]],
) extends GCMessage[T] with NoRefs

// /** A message asking its recipient to take a snapshot. */
// case object TakeSnapshot extends GCMessage[Nothing]

/**
 * A message sent by an actor to itself to check whether it's ready to
 * terminate.  
 */
case object SelfCheck extends GCMessage[Nothing] with NoRefs

/** 
 * A message sent by the garbage collector, indicating that this actor is
 * garbage.
 */
case object Kill extends GCMessage[Nothing] with NoRefs
