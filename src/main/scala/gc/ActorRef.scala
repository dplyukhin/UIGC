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
 * @tparam T The type of messages handled by the target actor. Must implement the [[Message]] interface.
 */

case class ActorRef[-T <: Message](token: Token,
                                   owner: AkkaActorRef[Nothing],
                                   target: AkkaActorRef[GCMessage[T]],
                                   ) {
  private var context: Option[ActorContext[_ <: Message]] = None

  def initialize[S <: Message](_context: ActorContext[S]): Unit = {
    context = Some(_context)
  }
  def !(msg : T) : Unit = {
    target.tell(AppMsg(msg, token))
    context.get.incSentCount(token)
  }

//  override def equals(obj: Any): Boolean = {
//    obj match {
//      case ActorRef(token, owner, target) => (
//        this.token == token && this.owner == owner && this.target == target
//        )
//      case _ => false
//    }
//  }
}

/**
 * A collection of all the [[ActorRef]]s an actor is aware of at a specific time.
 * @param knowledgeSet An actor's knowledge set, consisting of an ActorRef's refs, owners, and created sets.
 */
// TODO: split into fields
case class ActorSnapshot(knowledgeSet: Set[AnyActorRef])