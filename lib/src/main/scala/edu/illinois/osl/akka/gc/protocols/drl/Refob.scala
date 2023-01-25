package edu.illinois.osl.akka.gc.protocols.drl

import edu.illinois.osl.akka.gc.{proxy, Message}
import scala.annotation.unchecked.uncheckedVariance

/**
 * An opaque and globally unique token.
 *
 * @param ref The [[AkkaActorRef]] of the creator of the token
 * @param n A sequence number, unique for the creating actor
 */
case class Token(ref: Name, n: Int)

/**
 * A version of [[AkkaActorRef]] used to send messages to actors with GC enabled. It
 * should only be used by the *owner* to send messages to the *target.
 *
 * @param token A token that uniquely identifies this reference. If None, it represents an external actor.
 * @param owner The [[AkkaActorRef]] of the only actor that can use this reference. If None, it represents an external actor.
 * @param target The [[AkkaActorRef]] of the actor that will receive messages.
 * @tparam T The type of messages handled by the target actor. Must implement the [[Message]] interface.
 */
case class Refob[-T <: Message](
  token: Option[Token],
  owner: Option[proxy.ActorRef[GCMessage[Nothing]]],
  target: proxy.ActorRef[GCMessage[T]],
) extends DRL.IRefob[T] {
  private var state: Option[State] = None

  override def unsafeUpcast[U >: T @uncheckedVariance <: Message]: Refob[U] =
    this.asInstanceOf[Refob[U]]

  def initialize[S <: Message](_state: State): Unit = {
    state = Some(_state)
  }
  override def !(msg: T): Unit = {
    target ! AppMsg(msg, token)
    state.get.incSentCount(token)
  }

  override def rawActorRef: Name = target

  override def toString: String = {
    f"ActorRef#${token.hashCode()}: ${owner.get}->${target}"
  }

}