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
                                   ) extends AbstractRef[AkkaActorRef[Nothing], Token] {
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
  extends AbstractSnapshot[AkkaActorRef[Nothing], Token, AnyActorRef]