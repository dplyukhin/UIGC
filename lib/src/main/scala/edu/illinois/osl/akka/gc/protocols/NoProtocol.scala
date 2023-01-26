package edu.illinois.osl.akka.gc.protocols

import edu.illinois.osl.akka.gc.interfaces._
import akka.actor.typed.Signal
import scala.annotation.unchecked.uncheckedVariance
import akka.actor.ActorPath

object NoProtocol extends Protocol {
  type GCMessage[+T] = T
  case class Refob[-T](target: RefLike[T]) extends RefobLike[T] {
    override def !(msg: T, refs: Iterable[RefobLike[Nothing]]): Unit = 
      target ! msg
  }
  type SpawnInfo = Unit
  class State(
    val selfRef: Refob[Nothing]
  )

  /**
   * Transform a message from a non-GC actor so that it can be understood
   * by a GC actor. Necessarily, the recipient is a root actor.
   */
  def rootMessage[T](payload: T): GCMessage[T] = payload

  /** 
   * Produces SpawnInfo indicating to the actor that it is a root actor.
   */
  def rootSpawnInfo(): SpawnInfo = ()

  def initState[T](
    context: ContextLike[T],
    spawnInfo: SpawnInfo,
  ): State =
    new State(Refob(context.self))

  def getSelfRef[T](
    state: State,
    context: ContextLike[T]
  ): Refob[T] =
    state.selfRef.asInstanceOf[Refob[T]]

  def spawnImpl[S, T](
    factory: SpawnInfo => RefLike[S],
    state: State,
    ctx: ContextLike[T]
  ): Refob[S] =
    Refob(factory(()))

  def onMessage[T, Beh](
    msg: GCMessage[T],
    uponMessage: T => Beh,
    state: State,
    ctx: ContextLike[T],
  ): Protocol.TerminationDecision[Beh] =
    Protocol.ContinueWith(uponMessage(msg))

  def onSignal[T, Beh](
    signal: Signal, 
    uponSignal: Signal => Beh,
    state: State,
    ctx: ContextLike[T]
  ): Protocol.TerminationDecision[Beh] =
    Protocol.ContinueWith(uponSignal(signal))

  def createRef[S](
    target: Refob[S], owner: Refob[Nothing],
    state: State
  ): Refob[S] = 
    Refob(target.target)

  def release[S](
    releasing: Iterable[Refob[S]],
    state: State
  ): Unit = ()

  def releaseEverything(
    state: State
  ): Unit = ()
}
