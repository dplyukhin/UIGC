package edu.illinois.osl.uigc.engines

import akka.actor.ExtendedActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, Signal}
import edu.illinois.osl.uigc.interfaces

object Manual {
  trait SpawnInfo extends interfaces.SpawnInfo

  case class GCMessage[+T](payload: T, refs: Iterable[Refob[Nothing]])
      extends interfaces.GCMessage[T]

  case class Refob[-T](target: ActorRef[GCMessage[T]]) extends interfaces.Refob[T]

  case class Info() extends SpawnInfo

  class State(
      val selfRef: Refob[Nothing]
  ) extends interfaces.State
}

class Manual(system: ExtendedActorSystem) extends Engine {
  import Manual._

  override type GCMessageImpl[+T] = Manual.GCMessage[T]
  override type RefobImpl[-T] = Manual.Refob[T]
  override type SpawnInfoImpl = Manual.SpawnInfo
  override type StateImpl = Manual.State

  /** Transform a message from a non-GC actor so that it can be understood by a GC actor.
    * Necessarily, the recipient is a root actor.
    */
  def rootMessageImpl[T](payload: T, refs: Iterable[Refob[Nothing]]): GCMessage[T] =
    GCMessage(payload, refs)

  /** Produces SpawnInfo indicating to the actor that it is a root actor.
    */
  def rootSpawnInfoImpl(): SpawnInfo = Info()

  def initStateImpl[T](
      context: ActorContext[GCMessage[T]],
      spawnInfo: SpawnInfo
  ): State =
    new State(Refob(context.self))

  override def getSelfRefImpl[T](
      state: State,
      context: ActorContext[GCMessage[T]]
  ): Refob[T] =
    state.selfRef.asInstanceOf[Refob[T]]

  override def spawnImpl[S, T](
      factory: SpawnInfo => ActorRef[GCMessage[S]],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Refob[S] =
    Refob(factory(Info()))

  override def onMessageImpl[T](
      msg: GCMessage[T],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Option[T] =
    Some(msg.payload)

  override def onIdleImpl[T](
      msg: GCMessage[T],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Engine.TerminationDecision =
    Engine.ShouldContinue

  override def preSignalImpl[T](
      signal: Signal,
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Unit = ()

  override def postSignalImpl[T](
      signal: Signal,
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Engine.TerminationDecision =
    Engine.Unhandled

  override def createRefImpl[S, T](
      target: Refob[S],
      owner: Refob[Nothing],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Refob[S] =
    Refob(target.target)

  override def releaseImpl[S, T](
      releasing: Iterable[Refob[S]],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Unit = ()

  override def sendMessageImpl[T, S](
      ref: Refob[T],
      msg: T,
      refs: Iterable[Refob[Nothing]],
      state: State,
      ctx: ActorContext[GCMessage[S]]
  ): Unit =
    ref.target ! GCMessage(msg, refs)

}
