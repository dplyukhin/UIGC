package edu.illinois.osl.akka.gc
import edu.illinois.osl.akka.gc
import akka.actor.typed.scaladsl.{ActorContext => AkkaActorContext, Behaviors => AkkaBehaviors}
import akka.actor.typed.{Signal, ActorRef => AkkaActorRef, Behavior => AkkaBehavior}

trait Protocol {
  type GCMessage[+T <: Message]
  type ActorRef[-T <: Message] <: IActorRef[T]
  type SpawnInfo
  type State <: IState

  trait IActorRef[-T <: Message] {
    def !(msg: T): Unit
    def rawActorRef: AkkaActorRef[GCMessage[T]]
  }

  trait IState {
    val selfRef: ActorRef[Nothing]
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
    context: AkkaActorContext[GCMessage[T]],
    spawnInfo: SpawnInfo,
  ): State

  def spawnImpl[S <: Message, T <: Message](
    factory: SpawnInfo => AkkaActorRef[GCMessage[S]],
    state: State,
    ctx: AkkaActorContext[GCMessage[T]]
  ): ActorRef[S]

  def onMessage[T <: Message](
    msg: GCMessage[T],
    uponMessage: T => AkkaBehavior[GCMessage[T]],
    state: State,
    ctx: AkkaActorContext[GCMessage[T]]
  ): AkkaBehavior[GCMessage[T]]

  def onSignal[T <: Message](
    signal: Signal, 
    uponSignal: PartialFunction[Signal, Behavior[T]],
    state: State,
    ctx: AkkaActorContext[GCMessage[T]]
  ): Behavior[T]

  def createRef[S <: Message](
    target: ActorRef[S], owner: ActorRef[Nothing],
    state: State
  ): ActorRef[S]

  def release(
    releasing: Iterable[ActorRef[Nothing]],
    state: State
  ): Unit

  def releaseEverything(state: State): Unit
}
