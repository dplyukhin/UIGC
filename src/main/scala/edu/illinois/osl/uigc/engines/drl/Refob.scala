package edu.illinois.osl.uigc.engines.drl

import akka.actor.typed.ActorRef
import edu.illinois.osl.uigc.interfaces

/** An opaque and globally unique token.
  */
case class Token(ref: Name, n: Int)

case class Refob[-T](
    token: Option[Token],
    owner: Option[ActorRef[GCMessage[Nothing]]],
    target: ActorRef[GCMessage[T]]
) extends interfaces.Refob[T] {
  override def typedActorRef: ActorRef[interfaces.GCMessage[T]] =
    target.asInstanceOf[ActorRef[interfaces.GCMessage[T]]]
}
