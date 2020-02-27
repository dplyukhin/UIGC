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
 * @param x A token that uniquely identifies this reference.
 * @param owner The [[AkkaActorRef]] of the only actor that can use this reference.
 * @param target The [[AkkaActorRef]] of the actor that will receive messages.
 * @tparam T The type of messages handled by the target actor. Must implement the [[Message]] interface.
 */
class ActorRef[-T <: Message](val x: Token, val owner: AkkaActorRef[Nothing], val target: AkkaActorRef[GCMessage[T]]) {
  def !(msg : T) : Unit = target.tell(AppMsg(msg))
}
