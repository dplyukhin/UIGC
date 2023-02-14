package edu.illinois.osl.akka.gc.protocols

import edu.illinois.osl.akka.gc.interfaces._
import akka.actor.typed.Signal
import scala.annotation.unchecked.uncheckedVariance
import akka.actor.ActorPath

object NoProtocol extends Protocol {
  case class GCMessage[+T](payload: T, refs: Iterable[Refob[Nothing]]) extends Message with Pretty {
    def pretty: String = payload.toString
  }

  case class Refob[-T](target: RefLike[GCMessage[T]]) extends RefobLike[T] {
    override def pretty: String = target.pretty
  }

  type SpawnInfo = Unit

  class State(
    val selfRef: Refob[Nothing]
  ) extends Pretty {
    def pretty: String = "<nothing>"
  }

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

  def onMessage[T](
    msg: GCMessage[T],
    state: State,
    ctx: ContextLike[GCMessage[T]],
  ): Option[T] =
    Some(msg.payload)

  def onIdle[T](
    msg: GCMessage[T],
    state: State,
    ctx: ContextLike[GCMessage[T]],
  ): Protocol.TerminationDecision =
    Protocol.ShouldContinue 

  def preSignal[T](
    signal: Signal, 
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Unit = ()

  def postSignal[T](
    signal: Signal, 
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Protocol.TerminationDecision =
    Protocol.Unhandled

  def createRef[S,T](
    target: Refob[S], 
    owner: Refob[Nothing],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Refob[S] = 
    Refob(target.target)

  def release[S,T](
    releasing: Iterable[Refob[S]],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Unit = ()

  def releaseEverything[T](
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Unit = ()

  override def sendMessage[T, S](
    ref: Refob[T],
    msg: T,
    refs: Iterable[Refob[Nothing]],
    state: State,
    ctx: ContextLike[GCMessage[S]]
  ): Unit =
    ref.target ! GCMessage(msg, refs)
}
