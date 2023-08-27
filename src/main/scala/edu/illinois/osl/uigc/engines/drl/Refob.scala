package edu.illinois.osl.uigc.engines.drl

import edu.illinois.osl.uigc.interfaces._
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import scala.annotation.unchecked.uncheckedVariance

/**
 * An opaque and globally unique token.
 */
case class Token(ref: Name, n: Int)

case class Refob[-T](
  token: Option[Token],
  owner: Option[ActorRef[GCMessage[Nothing]]],
  target: ActorRef[GCMessage[T]],
) extends RefobLike[T]
