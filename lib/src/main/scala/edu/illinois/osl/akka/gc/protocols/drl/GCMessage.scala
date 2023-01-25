package edu.illinois.osl.akka.gc.protocols.drl

import akka.actor.typed.{ActorRef => AkkaActorRef, Behavior => AkkaBehavior, PostStop, Terminated, Signal}
import akka.actor.typed.scaladsl.{ActorContext => AkkaActorContext, Behaviors => AkkaBehaviors}
import scala.collection.mutable
import akka.actor.typed.SpawnProtocol
import edu.illinois.osl.akka.gc.{Protocol, Message, AnyActorRef, Behavior}

sealed trait GCMessage[+T <: Message]

final case class AppMsg[+T <: Message](
  payload: T, token: Option[Token]
) extends GCMessage[T]

final case class ReleaseMsg[+T <: Message](
  releasing: Iterable[ActorRef[Nothing]],
  created: Iterable[ActorRef[Nothing]],
) extends GCMessage[T]

/** A message asking its recipient to take a snapshot. */
case object TakeSnapshot extends GCMessage[Nothing]

/**
 * A message sent by an actor to itself to check whether it's ready to
 * terminate.  
 */
case object SelfCheck extends GCMessage[Nothing]

/** 
 * A message sent by the garbage collector, indicating that this actor is
 * garbage.
 */
case object Kill extends GCMessage[Nothing]