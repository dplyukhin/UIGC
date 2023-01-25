package edu.illinois.osl.akka.gc.protocols

import edu.illinois.osl.akka.gc.proxy
import edu.illinois.osl.akka.gc.{Protocol, Message, Behavior}
import akka.actor.typed.Signal
import scala.annotation.unchecked.uncheckedVariance

object NoProtocol extends Protocol {
  type GCMessage[+T <: Message] = T
  case class Refob[-T <: Message](rawActorRef: proxy.ActorRef[T]) extends IRefob[T] {
    override def !(msg: T): Unit = rawActorRef ! msg
    override def unsafeUpcast[U >: T @uncheckedVariance <: Message]: Refob[U] =
      this.asInstanceOf[Refob[U]]
  }
  type SpawnInfo = Unit
  class State(
    val selfRef: Refob[Nothing]
  ) extends IState

  /**
   * Transform a message from a non-GC actor so that it can be understood
   * by a GC actor. Necessarily, the recipient is a root actor.
   */
  def rootMessage[T <: Message](payload: T): GCMessage[T] = payload

  /** 
   * Produces SpawnInfo indicating to the actor that it is a root actor.
   */
  def rootSpawnInfo(): SpawnInfo = ()

  def initState[T <: Message](
    context: proxy.ActorContext[T],
    spawnInfo: SpawnInfo,
  ): State =
    new State(Refob(context.self))

  def spawnImpl[S <: Message, T <: Message](
    factory: SpawnInfo => proxy.ActorRef[S],
    state: State,
    ctx: proxy.ActorContext[T]
  ): Refob[S] =
    Refob(factory(()))

  def onMessage[T <: Message, Beh](
    msg: GCMessage[T],
    uponMessage: T => Beh,
    state: State,
    ctx: proxy.ActorContext[T],
  ): Protocol.TerminationDecision[Beh] =
    Protocol.ContinueWith(uponMessage(msg))

  def onSignal[T <: Message, Beh](
    signal: Signal, 
    uponSignal: Signal => Beh,
    state: State,
    ctx: proxy.ActorContext[T]
  ): Protocol.TerminationDecision[Beh] =
    Protocol.ContinueWith(uponSignal(signal))

  def createRef[S <: Message](
    target: Refob[S], owner: Refob[Nothing],
    state: State
  ): Refob[S] = 
    Refob(target.rawActorRef)

  def release[S <: Message](
    releasing: Iterable[Refob[S]],
    state: State
  ): Unit = ()

  def releaseEverything(
    state: State
  ): Unit = ()
}
