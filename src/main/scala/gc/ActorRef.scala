package gc

import akka.actor.typed.{ActorRef => AkkaActorRef}


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
                                   owner: Option[AkkaActorRef[Nothing]],
                                   target: AkkaActorRef[GCMessage[T]],
                                   ) {
  private var state: Option[ActorState[T, ActorName]] = None

  def initialize[S <: Message](_state: ActorState[T, ActorName]): Unit = {
    state = Some(_state)
  }
  def !(msg: T): Unit = {
    target ! AppMsg(msg, token)
    state.get.incSentCount(token)
  }

  override def toString: String = {
    f"ActorRef#${token.hashCode()}: ${owner.get.path.name}->${target.path.name}"
  }
}


/**
 * An instance of an actor's state.
 * @param refs [[ActorContext.refs]]
 * @param owners [[ActorContext.owners]]
 * @param created [[ActorContext.createdUsing]]'s values, flattened
 * @param releasedRefs [[ActorContext.released_owners]]
 * @param sentCounts [[ActorContext.sentCounts]]
 * @param recvCounts [[ActorContext.receivedCounts]]
 */
case class ActorSnapshot(refs: Set[AnyActorRef],
                         owners: Set[AnyActorRef],
                         created: Seq[AnyActorRef],
                         releasedRefs: Set[AnyActorRef],
                         sentCounts: Map[Token, Int],
                         recvCounts: Map[Token, Int])