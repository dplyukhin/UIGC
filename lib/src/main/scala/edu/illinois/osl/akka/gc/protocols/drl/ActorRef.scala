package edu.illinois.osl.akka.gc.protocols.drl

import akka.actor.typed.{ActorRef => AkkaActorRef, Behavior => AkkaBehavior, PostStop, Terminated, Signal}
import akka.actor.typed.scaladsl.{ActorContext => AkkaActorContext, Behaviors => AkkaBehaviors}
import scala.collection.mutable
import akka.actor.typed.SpawnProtocol
import edu.illinois.osl.akka.gc.{Protocol, Message, AnyActorRef, Behavior}

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
case class ActorRef[-T <: Message](
  token: Option[Token],
  owner: Option[AkkaActorRef[GCMessage[Nothing]]],
  target: AkkaActorRef[GCMessage[T]],
) extends DRL.IActorRef[T] {
  private var state: Option[State] = None

  def initialize[S <: Message](_state: State): Unit = {
    state = Some(_state)
  }
  override def !(msg: T): Unit = {
    target.tell(AppMsg(msg, token))
    state.get.incSentCount(token)
  }

  override def rawActorRef: AkkaActorRef[GCMessage[T]] =
    target

  override def toString: String = {
    f"ActorRef#${token.hashCode()}: ${owner.get.path.name}->${target.path.name}"
  }

}