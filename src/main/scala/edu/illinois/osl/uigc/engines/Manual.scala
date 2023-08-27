package edu.illinois.osl.uigc.engines

import edu.illinois.osl.uigc.interfaces._
import akka.actor.typed.{ActorRef, Signal}
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext

import scala.annotation.unchecked.uncheckedVariance
import akka.actor.{ActorPath, Address, ExtendedActorSystem}
import akka.remote.artery.{ObjectPool, OutboundEnvelope, ReusableOutboundEnvelope}

object Manual extends Engine {
  case class GCMessage[+T](payload: T, refs: Iterable[Refob[Nothing]]) extends Message with Pretty {
    def pretty: String = payload.toString
  }

  case class Refob[-T](target: ActorRef[GCMessage[T]]) extends RefobLike[T] {
    override def pretty: String = target.toString
  }

  trait SpawnInfo extends Serializable
  case class Info() extends SpawnInfo

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
  def rootSpawnInfo(): SpawnInfo = Info()

  def initState[T](
    context: ActorContext[GCMessage[T]],
    spawnInfo: SpawnInfo,
  ): State =
    new State(Refob(context.self))

  def getSelfRef[T](
    state: State,
    context: ActorContext[GCMessage[T]]
  ): Refob[T] =
    state.selfRef.asInstanceOf[Refob[T]]

  def spawnImpl[S, T](
    factory: SpawnInfo => ActorRef[GCMessage[S]],
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Refob[S] =
    Refob(factory(Info()))

  def onMessage[T](
    msg: GCMessage[T],
    state: State,
    ctx: ActorContext[GCMessage[T]],
  ): Option[T] =
    Some(msg.payload)

  def onIdle[T](
    msg: GCMessage[T],
    state: State,
    ctx: ActorContext[GCMessage[T]],
  ): Engine.TerminationDecision =
    Engine.ShouldContinue

  def preSignal[T](
    signal: Signal, 
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Unit = ()

  def postSignal[T](
    signal: Signal, 
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Engine.TerminationDecision =
    Engine.Unhandled

  def createRef[S,T](
    target: Refob[S], 
    owner: Refob[Nothing],
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Refob[S] = 
    Refob(target.target)

  def release[S,T](
    releasing: Iterable[Refob[S]],
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Unit = ()

  def releaseEverything[T](
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Unit = ()

  override def sendMessage[T, S](
    ref: Refob[T],
    msg: T,
    refs: Iterable[Refob[Nothing]],
    state: State,
    ctx: ActorContext[GCMessage[S]]
  ): Unit =
    ref.target ! GCMessage(msg, refs)


}
