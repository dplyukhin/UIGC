package edu.illinois.osl.akka.gc

import akka.actor.typed.Signal
import scala.annotation.unchecked.uncheckedVariance

object Protocol {
  sealed trait TerminationDecision[+Beh]
  case object ShouldStop extends TerminationDecision[Nothing]
  case object ShouldContinue extends TerminationDecision[Nothing]
  case class ContinueWith[Beh](beh: Beh) extends TerminationDecision[Beh]
}

trait Protocol {
  type GCMessage[+T <: Message]
  type Refob[-T <: Message] <: IRefob[T]
  type SpawnInfo
  type State <: IState

  trait IRefob[-T <: Message] {
    def !(msg: T): Unit
    def rawActorRef: proxy.ActorRef[Nothing]
    def unsafeUpcast[U >: T @uncheckedVariance <: Message]: Refob[U]
  }

  trait IState {
    val selfRef: Refob[Nothing]
  }

  /**
   * Transform a message from a non-GC actor so that it can be understood
   * by a GC actor. Necessarily, the recipient is a root actor.
   */
  def rootMessage[T <: Message](payload: T): GCMessage[T]

  /** 
   * Produces SpawnInfo indicating to the actor that it is a root actor.
   */
  def rootSpawnInfo(): SpawnInfo

  def initState[T <: Message](
    context: proxy.ActorContext[GCMessage[T]],
    spawnInfo: SpawnInfo,
  ): State

  def spawnImpl[S <: Message, T <: Message](
    factory: SpawnInfo => proxy.ActorRef[GCMessage[S]],
    state: State,
    ctx: proxy.ActorContext[GCMessage[T]]
  ): Refob[S]

  def onMessage[T <: Message, Beh](
    msg: GCMessage[T],
    uponMessage: T => Beh,
    state: State,
    ctx: proxy.ActorContext[GCMessage[T]]
  ): Protocol.TerminationDecision[Beh]

  def onSignal[T <: Message, Beh](
    signal: Signal, 
    uponSignal: Signal => Beh,
    state: State,
    ctx: proxy.ActorContext[GCMessage[T]]
  ): Protocol.TerminationDecision[Beh]

  def createRef[S <: Message](
    target: Refob[S], owner: Refob[Nothing],
    state: State
  ): Refob[S]

  def release[S <: Message](
    releasing: Iterable[Refob[S]],
    state: State
  ): Unit

  def releaseEverything(state: State): Unit
}
