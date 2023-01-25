package edu.illinois.osl.akka.gc.protocols
import edu.illinois.osl.akka.gc
import akka.actor.typed.scaladsl.{ActorContext => AkkaActorContext, Behaviors => AkkaBehaviors}
import akka.actor.typed.{Signal, ActorRef => AkkaActorRef, Behavior => AkkaBehavior}
import edu.illinois.osl.akka.gc.{Protocol, Message, AnyActorRef, Behavior}

object NoProtocol extends Protocol {
  type GCMessage[+T <: Message] = T
  case class ActorRef[-T <: Message](rawActorRef: AkkaActorRef[T]) extends IActorRef[T] {
    override def !(msg: T): Unit = rawActorRef ! msg
  }
  type SpawnInfo = Unit
  class State(val selfRef: ActorRef[Nothing]) extends IState

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
    context: AkkaActorContext[GCMessage[T]],
    spawnInfo: SpawnInfo,
  ): State =
    new State(ActorRef(context.self))

  def spawnImpl[S <: Message, T <: Message](
    factory: SpawnInfo => AkkaActorRef[GCMessage[S]],
    state: State,
    ctx: AkkaActorContext[GCMessage[T]]
  ): ActorRef[S] =
    ActorRef(factory(()))

  def onMessage[T <: Message](
    msg: GCMessage[T],
    uponMessage: T => AkkaBehavior[GCMessage[T]],
    state: State,
    ctx: AkkaActorContext[GCMessage[T]]
  ): AkkaBehavior[GCMessage[T]] =
    uponMessage(msg)

  def onSignal[T <: Message](
    signal: Signal, 
    uponSignal: PartialFunction[Signal, Behavior[T]],
    state: State,
    ctx: AkkaActorContext[GCMessage[T]]
  ): Behavior[T] =
    uponSignal.applyOrElse[Signal, Behavior[T]](signal, _ => AkkaBehaviors.same)

  def createRef[S <: Message](
    target: ActorRef[S], owner: ActorRef[Nothing],
    state: State
  ): ActorRef[S] = 
    ActorRef(target.rawActorRef)

  def release(
    releasing: Iterable[ActorRef[Nothing]],
    state: State
  ): Unit = ()

  def releaseEverything(state: State): Unit = ()
}
