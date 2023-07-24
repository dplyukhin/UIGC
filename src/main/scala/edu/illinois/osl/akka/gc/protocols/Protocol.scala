package edu.illinois.osl.akka.gc.protocols

import akka.actor.{Address, ExtendedActorSystem}
import akka.actor.typed.{ActorRef, Signal}
import akka.actor.typed.scaladsl.ActorContext
import akka.remote.artery.{InboundEnvelope, OutboundEnvelope}

import scala.annotation.unchecked.uncheckedVariance
import edu.illinois.osl.akka.gc.interfaces._

object Protocol {
  sealed trait TerminationDecision
  case object ShouldStop extends TerminationDecision
  case object ShouldContinue extends TerminationDecision
  case object Unhandled extends TerminationDecision
}

trait Protocol {
  type GCMessage[+T] <: Message with Pretty
  type Refob[-T] <: RefobLike[T]
  type SpawnInfo
  type State <: Pretty
  type IngressState
  type EgressState

  /**
   * Transform a message from a non-GC actor so that it can be understood
   * by a GC actor. Necessarily, the recipient is a root actor.
   */
  def rootMessage[T](payload: T, refs: Iterable[RefobLike[Nothing]]): GCMessage[T]

  /** 
   * Produces SpawnInfo indicating to the actor that it is a root actor.
   */
  def rootSpawnInfo(): SpawnInfo

  def initState[T](
    context: ActorContext[GCMessage[T]],
    spawnInfo: SpawnInfo,
  ): State

  def getSelfRef[T](
    state: State,
    context: ActorContext[GCMessage[T]]
  ): Refob[T]

  def spawnImpl[S, T](
     factory: SpawnInfo => ActorRef[GCMessage[S]],
     state: State,
     ctx: ActorContext[GCMessage[T]]
  ): Refob[S]

  def sendMessage[T,S](
    ref: Refob[T],
    msg: T,
    refs: Iterable[Refob[Nothing]],
    state: State,
    ctx: ActorContext[GCMessage[S]]
  ): Unit

  def onMessage[T](
    msg: GCMessage[T],
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Option[T]

  def onIdle[T](
    msg: GCMessage[T],
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Protocol.TerminationDecision

  def preSignal[T](
    signal: Signal, 
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Unit

  def postSignal[T](
    signal: Signal, 
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Protocol.TerminationDecision

  def createRef[S,T](
    target: Refob[S], 
    owner: Refob[Nothing],
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Refob[S]

  def release[S,T](
    releasing: Iterable[Refob[S]],
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Unit

  def releaseEverything[T](
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Unit

  def spawnIngress(
      system: ExtendedActorSystem,
      adjacent: Address
  ): IngressState

  def spawnEgress(
    system: ExtendedActorSystem,
    adjacent: Address
  ): EgressState

  def onIngressEnvelope(
      state: IngressState,
      msg: InboundEnvelope,
      push: InboundEnvelope => Unit
  ): Unit = push(msg)

  def onEgressEnvelope(
    state: EgressState,
    msg: OutboundEnvelope,
    push: OutboundEnvelope => Unit
  ): Unit = push(msg)
}
