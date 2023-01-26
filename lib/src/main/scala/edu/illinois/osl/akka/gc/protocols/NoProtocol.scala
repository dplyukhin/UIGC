package edu.illinois.osl.akka.gc.protocols

import edu.illinois.osl.akka.gc.interfaces._
import akka.actor.typed.Signal
import scala.annotation.unchecked.uncheckedVariance
import akka.actor.ActorPath

object NoProtocol extends Protocol {
  case class GCMessage[+T](payload: T, refs: Iterable[Refob[Nothing]]) extends Message

  case class Refob[-T](target: RefLike[GCMessage[T]]) extends RefobLike[T] {
    override def !(msg: T, refs: Iterable[RefobLike[Nothing]]): Unit = 
      target ! GCMessage(msg, refs.asInstanceOf[Iterable[Refob[Nothing]]])
  }
  type SpawnInfo = Unit
  class State(
    val selfRef: Refob[Nothing]
  )

  /**
   * Transform a message from a non-GC actor so that it can be understood
   * by a GC actor. Necessarily, the recipient is a root actor.
   */
  def rootMessage[T](payload: T, refs: Iterable[RefobLike[Nothing]]): GCMessage[T] = 
    GCMessage(payload, refs.asInstanceOf[Iterable[Refob[Nothing]]])

  /** 
   * Produces SpawnInfo indicating to the actor that it is a root actor.
   */
  def rootSpawnInfo(): SpawnInfo = ()

  def initState[T](
    context: ContextLike[GCMessage[T]],
    spawnInfo: SpawnInfo,
  ): State =
    new State(Refob(context.self))

  def getSelfRef[T](
    state: State,
    context: ContextLike[GCMessage[T]]
  ): Refob[T] =
    state.selfRef.asInstanceOf[Refob[T]]

  def spawnImpl[S, T](
    factory: SpawnInfo => RefLike[GCMessage[S]],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Refob[S] =
    Refob(factory(()))

  def onMessage[T, Beh](
    msg: GCMessage[T],
    uponMessage: T => Beh,
    state: State,
    ctx: ContextLike[GCMessage[T]],
  ): Protocol.TerminationDecision[Beh] =
    Protocol.ContinueWith(uponMessage(msg.payload))

  def onSignal[T, Beh](
    signal: Signal, 
    uponSignal: Signal => Beh,
    state: State,
    ctx: ContextLike[GCMessage[T]]
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
