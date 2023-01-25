package edu.illinois.osl.akka.gc.protocols

import edu.illinois.osl.akka.gc.raw
import edu.illinois.osl.akka.gc.{Protocol, Message, Behavior}
import akka.actor.typed.Signal

object NoProtocol extends Protocol {
  type GCMessage[+T <: Message] = T
  case class Refob[-T <: Message](rawActorRef: raw.ActorRef[T]) extends IRefob[T] {
    override def !(msg: T): Unit = rawActorRef ! msg
  }
  type SpawnInfo = Unit
  class State(val selfRef: Refob[Nothing]) extends IState

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
    context: raw.ActorContext[T],
    spawnInfo: SpawnInfo,
  ): State =
    new State(Refob(context.self))

  def spawnImpl[S <: Message, T <: Message](
    factory: SpawnInfo => raw.ActorRef[S],
    state: State,
    ctx: raw.ActorContext[T]
  ): Refob[S] =
    Refob(factory(()))

  def onMessage[T <: Message](
    msg: GCMessage[T],
    uponMessage: T => raw.Behavior[T],
    state: State,
    ctx: raw.ActorContext[T],
  ): raw.Behavior[T] =
    uponMessage(msg)

  def onSignal[T <: Message](
    signal: Signal, 
    uponSignal: PartialFunction[Signal, Behavior[T]],
    state: State,
    ctx: raw.ActorContext[T]
  ): Behavior[T] =
    uponSignal.applyOrElse[Signal, Behavior[T]](signal, _ => raw.Behaviors.same)

  def createRef[S <: Message](
    target: Refob[S], owner: Refob[Nothing],
    state: State
  ): Refob[S] = 
    Refob(target.rawActorRef)

  def release(
    releasing: Iterable[Refob[Nothing]],
    state: State
  ): Unit = ()

  def releaseEverything(state: State): Unit = ()
}
