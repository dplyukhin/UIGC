package edu.illinois.osl.uigc.engines

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, Signal}
import akka.actor.{Address, ExtendedActorSystem}
import akka.remote.artery.{InboundEnvelope, ObjectPool, OutboundEnvelope, ReusableOutboundEnvelope}
import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import akka.stream.{FlowShape, Inlet, Outlet}
import edu.illinois.osl.uigc.interfaces._

object Engine {
  sealed trait TerminationDecision
  case object ShouldStop extends TerminationDecision
  case object ShouldContinue extends TerminationDecision
  case object Unhandled extends TerminationDecision
}

/** A GC engine is a collection of hooks and datatypes, used by the UIGC API. */
trait Engine {
  /**
   * UIGC actors typically handle two types of messages: "Application messages"
   * sent by the user's application, and "control messages" sent by the GC engine.
   * Control messages are handled transparently by the UIGC middleware.
   *
   * The [[GCMessage]] type is a supertype of control messages and application messages.
   *
   * @tparam T The type of application messages
   */
  type GCMessage[+T] <: Message with Pretty

  /**
   * In Akka, two actors on the same ActorSystem can share the same
   * [[akka.actor.typed.ActorRef]]. In most actor GCs, it's important that actors
   * do not share references. It's therefore useful to create a wrapper for ActorRefs.
   * In UIGC, this wrapper is called a [[Refob]] (short for "reference object").
   *
   * @tparam T The type of application messages handled by this actor
   */
  type Refob[-T] <: RefobLike[T]

  /**
   * When one actor spawns another, some actor GCs need the parent to pass information
   * to the child. [[SpawnInfo]] is an abstract type that represents that information.
   */
  type SpawnInfo

  /**
   * Most actor GCs need to store some state at each actor. That state is represented
   * by the [[State]] type.
   */
  type State <: Pretty

  /** Transform a message from a non-GC actor so that it can be understood by a GC actor.
    * Necessarily, the recipient is a root actor.
    */
  def rootMessage[T](payload: T, refs: Iterable[RefobLike[Nothing]]): GCMessage[T]

  /** Produces SpawnInfo indicating to the actor that it is a root actor.
    */
  def rootSpawnInfo(): SpawnInfo

  def initState[T](
      context: ActorContext[GCMessage[T]],
      spawnInfo: SpawnInfo
  ): State

  def getSelfRef[T](
      state: State,
      context: ActorContext[GCMessage[T]]
  ): Refob[T]

  def spawnImpl[S, T](
      factory: SpawnInfo => ActorRef[GCMessage[S]],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Refob[S]

  def sendMessage[T, S](
      ref: Refob[T],
      msg: T,
      refs: Iterable[Refob[Nothing]],
      state: State,
      ctx: ActorContext[GCMessage[S]]
  ): Unit

  def onMessage[T](
      msg: GCMessage[T],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Option[T]

  def onIdle[T](
      msg: GCMessage[T],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Engine.TerminationDecision

  def preSignal[T](
      signal: Signal,
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Unit

  def postSignal[T](
      signal: Signal,
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Engine.TerminationDecision

  def createRef[S, T](
      target: Refob[S],
      owner: Refob[Nothing],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Refob[S]

  def release[S, T](
      releasing: Iterable[Refob[S]],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Unit

  def releaseEverything[T](
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Unit

  def spawnIngress(
      in: Inlet[InboundEnvelope],
      out: Outlet[InboundEnvelope],
      shape: FlowShape[InboundEnvelope, InboundEnvelope],
      system: ExtendedActorSystem,
      adjacent: Address
  ): GraphStageLogic =
    new GraphStageLogic(shape) {
      setHandler(
        in,
        new InHandler {
          override def onPush(): Unit = {
            val msg = grab(in)
            push(out, msg)
          }
        }
      )
      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit =
            pull(in)
        }
      )
    }

  def spawnEgress(
      in: Inlet[OutboundEnvelope],
      out: Outlet[OutboundEnvelope],
      shape: FlowShape[OutboundEnvelope, OutboundEnvelope],
      system: ExtendedActorSystem,
      adjacent: Address,
      outboundObjectPool: ObjectPool[ReusableOutboundEnvelope]
  ): GraphStageLogic =
    new GraphStageLogic(shape) {
      setHandler(
        in,
        new InHandler {
          override def onPush(): Unit = {
            val msg = grab(in)
            push(out, msg)
          }
        }
      )
      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit =
            pull(in)
        }
      )
    }
}
