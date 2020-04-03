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
 * @param token A token that uniquely identifies this reference.
 * @param owner The [[AkkaActorRef]] of the only actor that can use this reference.
 * @param target The [[AkkaActorRef]] of the actor that will receive messages.
 * @param lastContext The [[ActorContext]] this reference was most recently received in.
 * @tparam T The type of messages handled by the target actor. Must implement the [[Message]] interface.
 */
case class ActorRef[-T <: Message](token: Token,
                                   owner: AkkaActorRef[Nothing],
                                   target: AkkaActorRef[GCMessage[T]],
                                   var lastContext: ActorContext[T]) {
  def !(msg : T) : Unit = {
    target.tell(AppMsg(msg, token))
    lastContext.refUsedSent(token)
  }
}

// TODO: Add epochs to Snapshots
/**
 * A collection of all the [[ActorRef]]s an actor is aware of at a specific time.
 * @param knowledgeSet An actor's knowledge set, consisting of an ActorRef's refs, owners, and created sets.
 */
case class ActorSnapshot(knowledgeSet: Set[AnyActorRef])