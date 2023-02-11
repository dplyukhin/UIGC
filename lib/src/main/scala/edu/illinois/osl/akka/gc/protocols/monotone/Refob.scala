package edu.illinois.osl.akka.gc.protocols.monotone

import edu.illinois.osl.akka.gc.interfaces._
import scala.annotation.unchecked.uncheckedVariance

/**
 * An opaque and globally unique token.
 *
 * @param ref The [[AkkaActorRef]] of the creator of the token
 * @param n A sequence number, unique for the creating actor
 */
case class Token(ref: Name, n: Int) extends Pretty {
  def pretty: String = s"Token#${Math.floorMod(this.hashCode(), 1000)}"
}

/**
 * A version of [[AkkaActorRef]] used to send messages to actors with GC enabled. It
 * should only be used by the *owner* to send messages to the *target.
 *
 * @param token A token that uniquely identifies this reference. If None, it represents an external actor.
 * @param owner The [[AkkaActorRef]] of the only actor that can use this reference. If None, it represents an external actor.
 * @param target The [[AkkaActorRef]] of the actor that will receive messages.
 * @tparam T The type of messages handled by the target actor. Must implement the [[Message]] interface.
 */
case class Refob[-T](
  token: Option[Token],
  owner: Option[RefLike[GCMessage[Nothing]]],
  target: RefLike[GCMessage[T]],
) extends RefobLike[T] {
  var info: RefobInfo = RefobInfo.activeRefob

  override def !(msg: T, refs: Iterable[RefobLike[Nothing]]): Unit = {
    target ! AppMsg(msg, token, refs.asInstanceOf[Iterable[Refob[Nothing]]])
    RefobInfo.incSendCount(info)
  }

  override def pretty: String = {
    f"<Refob#${Math.floorMod(token.hashCode(), 1000)}: ${owner.get.pretty} -> ${target.pretty}>"
  }

}