package edu.illinois.osl.uigc.engines

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, Signal}
import akka.actor.{Address, ExtendedActorSystem, Extension}
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
trait Engine extends Extension {
  type RefobImpl[T] <: Refob[T]
  type GCMessageImpl[T] <: GCMessage[T]
  type SpawnInfoImpl <: SpawnInfo
  type StateImpl <: State

  /** Transform a message from a non-GC actor so that it can be understood by a GC actor.
    * Necessarily, the recipient is a root actor.
    */
  final def rootMessage[T](payload: T, refs: Iterable[Refob[Nothing]]): GCMessage[T] =
    rootMessageImpl(payload, refs.asInstanceOf[Iterable[RefobImpl[Nothing]]])

  def rootMessageImpl[T](payload: T, refs: Iterable[RefobImpl[Nothing]]): GCMessage[T]

  /** Produces SpawnInfo indicating to the actor that it is a root actor.
    */
  final def rootSpawnInfo(): SpawnInfo =
    rootSpawnInfoImpl()

  def rootSpawnInfoImpl(): SpawnInfoImpl

  /** Given an [[ActorRef]] pointing to a root actor, produce a [[Refob]]. */
  final def toRootRefob[T](ref: ActorRef[GCMessage[T]]): Refob[T] =
    toRefobImpl(ref)

  def toRefobImpl[T](ref: ActorRef[GCMessageImpl[T]]): RefobImpl[T]

  /** Compute the initial GC state of a managed actor.
    */
  final def initState[T](
      context: ActorContext[GCMessage[T]],
      spawnInfo: SpawnInfo
  ): State =
    initStateImpl(
      context.asInstanceOf[ActorContext[GCMessageImpl[T]]],
      spawnInfo.asInstanceOf[SpawnInfoImpl]
    )

  def initStateImpl[T](
      context: ActorContext[GCMessageImpl[T]],
      spawnInfo: SpawnInfoImpl
  ): StateImpl

  /** Get a refob owned by this actor, pointing to itself.
    */
  final def getSelfRef[T](
      state: State,
      context: ActorContext[GCMessage[T]]
  ): Refob[T] =
    getSelfRefImpl(
      state.asInstanceOf[StateImpl],
      context.asInstanceOf[ActorContext[GCMessageImpl[T]]]
    )

  def getSelfRefImpl[T](
      state: StateImpl,
      context: ActorContext[GCMessageImpl[T]]
  ): RefobImpl[T]

  /** Spawn a managed actor. */
  final def spawn[S, T](
      factory: SpawnInfo => ActorRef[GCMessage[S]],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Refob[S] =
    spawnImpl(
      factory.asInstanceOf[SpawnInfoImpl => ActorRef[GCMessageImpl[S]]],
      state.asInstanceOf[StateImpl],
      ctx.asInstanceOf[ActorContext[GCMessageImpl[T]]]
    )

  def spawnImpl[S, T](
      factory: SpawnInfoImpl => ActorRef[GCMessageImpl[S]],
      state: StateImpl,
      ctx: ActorContext[GCMessageImpl[T]]
  ): RefobImpl[S]

  /** Send a message to a managed actor. */
  final def sendMessage[T, S](
      ref: Refob[T],
      msg: T,
      refs: Iterable[Refob[Nothing]],
      state: State,
      ctx: ActorContext[GCMessage[S]]
  ): Unit =
    sendMessageImpl(
      ref.asInstanceOf[RefobImpl[T]],
      msg,
      refs.asInstanceOf[Iterable[RefobImpl[Nothing]]],
      state.asInstanceOf[StateImpl],
      ctx.asInstanceOf[ActorContext[GCMessageImpl[T]]]
    )

  def sendMessageImpl[T, S](
      ref: RefobImpl[T],
      msg: T,
      refs: Iterable[RefobImpl[Nothing]],
      state: StateImpl,
      ctx: ActorContext[GCMessageImpl[S]]
  ): Unit

  final def onMessage[T](
      msg: GCMessage[T],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Option[T] =
    onMessageImpl(
      msg.asInstanceOf[GCMessageImpl[T]],
      state.asInstanceOf[StateImpl],
      ctx.asInstanceOf[ActorContext[GCMessageImpl[T]]]
    )

  def onMessageImpl[T](
      msg: GCMessageImpl[T],
      state: StateImpl,
      ctx: ActorContext[GCMessageImpl[T]]
  ): Option[T]

  final def onIdle[T](
      msg: GCMessage[T],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Engine.TerminationDecision =
    onIdleImpl(
      msg.asInstanceOf[GCMessageImpl[T]],
      state.asInstanceOf[StateImpl],
      ctx.asInstanceOf[ActorContext[GCMessageImpl[T]]]
    )

  def onIdleImpl[T](
      msg: GCMessageImpl[T],
      state: StateImpl,
      ctx: ActorContext[GCMessageImpl[T]]
  ): Engine.TerminationDecision

  final def preSignal[T](
      signal: Signal,
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Unit =
    preSignalImpl(
      signal,
      state.asInstanceOf[StateImpl],
      ctx.asInstanceOf[ActorContext[GCMessageImpl[T]]]
    )

  def preSignalImpl[T](
      signal: Signal,
      state: StateImpl,
      ctx: ActorContext[GCMessageImpl[T]]
  ): Unit

  final def postSignal[T](
      signal: Signal,
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Engine.TerminationDecision =
    postSignalImpl(
      signal,
      state.asInstanceOf[StateImpl],
      ctx.asInstanceOf[ActorContext[GCMessageImpl[T]]]
    )

  def postSignalImpl[T](
      signal: Signal,
      state: StateImpl,
      ctx: ActorContext[GCMessageImpl[T]]
  ): Engine.TerminationDecision

  final def createRef[S, T](
      target: Refob[S],
      owner: Refob[Nothing],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Refob[S] =
    createRefImpl(
      target.asInstanceOf[RefobImpl[S]],
      owner.asInstanceOf[RefobImpl[Nothing]],
      state.asInstanceOf[StateImpl],
      ctx.asInstanceOf[ActorContext[GCMessageImpl[T]]]
    )

  def createRefImpl[S, T](
      target: RefobImpl[S],
      owner: RefobImpl[Nothing],
      state: StateImpl,
      ctx: ActorContext[GCMessageImpl[T]]
  ): Refob[S]

  final def release[S, T](
      releasing: Iterable[Refob[S]],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Unit =
    releaseImpl(
      releasing.asInstanceOf[Iterable[RefobImpl[Nothing]]],
      state.asInstanceOf[StateImpl],
      ctx.asInstanceOf[ActorContext[GCMessageImpl[T]]]
    )

  def releaseImpl[S, T](
      releasing: Iterable[RefobImpl[S]],
      state: StateImpl,
      ctx: ActorContext[GCMessageImpl[T]]
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
