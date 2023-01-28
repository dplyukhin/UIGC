package edu.illinois.osl.akka.gc.protocols

import akka.actor.typed.Signal
import scala.annotation.unchecked.uncheckedVariance
import edu.illinois.osl.akka.gc.interfaces._

object Protocol {
  sealed trait TerminationDecision
  case object ShouldStop extends TerminationDecision
  case object ShouldContinue extends TerminationDecision
  case object Unhandled extends TerminationDecision
}

trait Protocol {
  type GCMessage[+T] <: Message
  type Refob[-T] <: RefobLike[T]
  type SpawnInfo
  type State

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
    context: ContextLike[GCMessage[T]],
    spawnInfo: SpawnInfo,
  ): State

  def getSelfRef[T](
    state: State,
    context: ContextLike[GCMessage[T]]
  ): Refob[T]

  def spawnImpl[S, T](
    factory: SpawnInfo => RefLike[GCMessage[S]],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Refob[S]

  def onMessage[T](
    msg: GCMessage[T],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Option[T]

  def onIdle[T](
    msg: GCMessage[T],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Protocol.TerminationDecision

  def preSignal[T](
    signal: Signal, 
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Unit

  def postSignal[T](
    signal: Signal, 
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Protocol.TerminationDecision

  def createRef[S](
    target: Refob[S], owner: Refob[Nothing],
    state: State
  ): Refob[S]

  def release[S](
    releasing: Iterable[Refob[S]],
    state: State
  ): Unit

  def releaseEverything(state: State): Unit

  /** Idempotent function needed by DRL for model checking. */
  def initializeRefob[T](
    refob: Refob[Nothing],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Unit
}
