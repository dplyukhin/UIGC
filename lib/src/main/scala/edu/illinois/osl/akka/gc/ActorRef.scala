package edu.illinois.osl.akka.gc

import akka.actor.typed.{ActorRef => AkkaActorRef}
import edu.illinois.osl.akka.gc.detector.{AbstractRef, AbstractSnapshot}

/**
 * An opaque and globally unique token.
 *
 * @param ref The [[AkkaActorRef]] of the creator of the token
 * @param n A sequence number, unique for the creating actor
 */
case class Token(ref: AkkaActorRef[Nothing], n: Int)

/**
 * A version of [[AkkaActorRef]] used to send messages to actors with GC enabled. It
 * should only be used by the *owner* to send messages to the *target.
 *
 * @param token A token that uniquely identifies this reference. If None, it represents an external actor.
 * @param owner The [[AkkaActorRef]] of the only actor that can use this reference. If None, it represents an external actor.
 * @param target The [[AkkaActorRef]] of the actor that will receive messages.
 * @tparam T The type of messages handled by the target actor. Must implement the [[Message]] interface.
 */

case class ActorRef[-T <: Message](token: Option[Token],
                                   owner: Option[AkkaActorRef[GCMessage[Nothing]]],
                                   target: AkkaActorRef[GCMessage[T]],
                                   ) extends AbstractRef[ActorName, Token] {
  private var context: Option[ActorContext[_ <: Message]] = None

  def initialize[S <: Message](_context: ActorContext[S]): Unit = {
    context = Some(_context)
  }
  def !(msg: T): Unit = {
    target.tell(AppMsg(msg, token))
    context.get.incSentCount(token)
  }

  override def toString: String = {
    f"ActorRef#${token.hashCode()}: ${owner.get.path.name}->${target.path.name}"
  }

}

case class ActorSnapshot(refs: Iterable[AnyActorRef],
                         owners: Iterable[AnyActorRef],
                         created: Iterable[AnyActorRef],
                         releasedRefs: Iterable[AnyActorRef],
                         sentCounts: Map[Token, Int],
                         recvCounts: Map[Token, Int])
  extends AbstractSnapshot[ActorName, Token, AnyActorRef]