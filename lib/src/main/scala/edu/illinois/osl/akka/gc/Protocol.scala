package edu.illinois.osl.akka.gc

import akka.actor.typed.Signal

trait Protocol {
  type GCMessage[+T <: Message]
  type Refob[-T <: Message] <: IRefob[T]
  type SpawnInfo
  type State <: IState

  trait IRefob[-T <: Message] {
    def !(msg: T): Unit
    def rawActorRef: ActorName
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
    context: raw.ActorContext[GCMessage[T]],
    spawnInfo: SpawnInfo,
  ): State

  def spawnImpl[S <: Message, T <: Message](
    factory: SpawnInfo => raw.ActorRef[GCMessage[S]],
    state: State,
    ctx: raw.ActorContext[GCMessage[T]]
  ): Refob[S]

  def onMessage[T <: Message](
    msg: GCMessage[T],
    uponMessage: T => raw.Behavior[GCMessage[T]],
    state: State,
    ctx: raw.ActorContext[GCMessage[T]]
  ): raw.Behavior[GCMessage[T]]

  def onSignal[T <: Message](
    signal: Signal, 
    uponSignal: PartialFunction[Signal, Behavior[T]],
    state: State,
    ctx: raw.ActorContext[GCMessage[T]]
  ): Behavior[T]

  def createRef[S <: Message](
    target: Refob[S], owner: Refob[Nothing],
    state: State
  ): Refob[S]

  def release(
    releasing: Iterable[Refob[Nothing]],
    state: State
  ): Unit

  def releaseEverything(state: State): Unit
}
